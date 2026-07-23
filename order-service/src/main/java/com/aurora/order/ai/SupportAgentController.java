package com.aurora.order.ai;

import com.aurora.order.exception.InvalidRequestException;
import com.aurora.order.exception.UpstreamUnavailableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// §10.4: Gemini function-calling ile salt-okunur sipariş destek asistanı.
// Gemini SADECE üç salt-okunur tool + bir "öner" sinyali çağırabilir; hiçbir tool
// state değiştirmez. Gerçek iptal yalnızca insan onayından sonra, ayrı ve sabit bir
// endpoint olan POST /orders/{id}/cancel üzerinden yapılır.
@RestController
@RequestMapping("/support")
public class SupportAgentController {

    private static final int MAX_TOOL_ROUNDS = 5;

    private final GeminiClient geminiClient;
    private final SupportToolsService tools;

    public SupportAgentController(GeminiClient geminiClient, SupportToolsService tools) {
        this.geminiClient = geminiClient;
        this.tools = tools;
    }

    @PostMapping
    public ResponseEntity<?> support(@RequestBody(required = false) Map<String, Object> body) {
        String question = body == null ? null : (String) body.get("question");
        if (question == null || question.isBlank()) {
            throw new InvalidRequestException();
        }
        if (!geminiClient.isConfigured()) {
            throw new UpstreamUnavailableException("google_api_key_not_configured", null);
        }

        Long customerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());

        List<Map<String, Object>> contents = new java.util.ArrayList<>();
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", question))));

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Map<String, Object> response = geminiClient.generateContent(Map.of(
                    "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction()))),
                    "tools", List.of(functionDeclarations()),
                    "contents", contents
            ));

            Map<String, Object> modelContent = extractModelContent(response);
            List<Map<String, Object>> parts = castParts(modelContent.get("parts"));

            Map<String, Object> functionCall = findFunctionCall(parts);
            if (functionCall == null) {
                String text = findText(parts);
                return ResponseEntity.ok(Map.of("answer", text != null ? text : "Bunu şu anda yanıtlayamıyorum."));
            }

            String name = (String) functionCall.get("name");
            String callId = (String) functionCall.get("id");
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) functionCall.getOrDefault("args", Map.of());

            if ("propose_cancellation".equals(name)) {
                return handleProposeCancellation(args, customerId);
            }

            Map<String, Object> toolResult = executeReadOnlyTool(name, args, customerId);

            // Modelin bu turdaki cevabını (thoughtSignature dahil) AYNEN geri ekle —
            // yoksa Gemini 3.x modelleri bir sonraki turda 400 döner.
            contents.add(modelContent);
            contents.add(Map.of("role", "user", "parts", List.of(
                    Map.of("functionResponse", functionResponseObject(name, callId, toolResult))
            )));
        }

        // Model 5 turda da bir cevaba/öneriye varamadı — bunu altyapı sorunu say.
        throw new UpstreamUnavailableException("gemini_did_not_converge", null);
    }

    private ResponseEntity<?> handleProposeCancellation(Map<String, Object> args, Long customerId) {
        Long orderId = ((Number) args.get("orderId")).longValue();
        String summary = (String) args.getOrDefault("summary", "Siparişi iptal etmek ister misiniz?");

        // propose_cancellation'ı GERÇEKTEN icra etmiyoruz — sadece öneriyoruz. Ama önerinin
        // anlamlı olması için model orderId'yi uydurmuş olabilir ihtimaline karşı burada
        // bağımsızca doğruluyoruz: sipariş gerçekten bu müşteriye ait ve 'pending' mi?
        Map<String, Object> status = tools.getOrderStatus(orderId, customerId);
        boolean eligible = Boolean.TRUE.equals(status.get("found")) && "pending".equals(status.get("status"));
        if (!eligible) {
            return ResponseEntity.ok(Map.of(
                    "answer", "Bu siparişi şu anda iptal olarak önerilemiyorum — bulunamadı ya da artık 'beklemede' durumunda değil."
            ));
        }

        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("action", "cancel");
        proposal.put("orderId", orderId);
        proposal.put("summary", summary);
        proposal.put("requiresConfirmation", true);
        return ResponseEntity.ok(proposal);
    }

    private Map<String, Object> executeReadOnlyTool(String name, Map<String, Object> args, Long customerId) {
        return switch (name) {
            case "get_order_status" -> tools.getOrderStatus(((Number) args.get("orderId")).longValue(), customerId);
            case "check_stock" -> tools.checkStock(((Number) args.get("productId")).longValue());
            case "estimate_restock" -> tools.estimateRestock(((Number) args.get("productId")).longValue());
            default -> Map.of("error", "unknown_tool");
        };
    }

    private Map<String, Object> functionResponseObject(String name, String callId, Map<String, Object> result) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("name", name);
        if (callId != null) obj.put("id", callId);
        obj.put("response", result);
        return obj;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractModelContent(Map<String, Object> response) {
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new UpstreamUnavailableException("gemini_empty_response", null);
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) {
            throw new UpstreamUnavailableException("gemini_empty_response", null);
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castParts(Object parts) {
        return parts == null ? List.of() : (List<Map<String, Object>>) parts;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findFunctionCall(List<Map<String, Object>> parts) {
        for (Map<String, Object> part : parts) {
            Object fc = part.get("functionCall");
            if (fc != null) return (Map<String, Object>) fc;
        }
        return null;
    }

    private String findText(List<Map<String, Object>> parts) {
        for (Map<String, Object> part : parts) {
            Object text = part.get("text");
            if (text != null) return (String) text;
        }
        return null;
    }

    private String systemInstruction() {
        return """
                Sen Aurora Commerce'in sipariş destek asistanısın. SADECE sağlanan tool'ları \
                kullanarak yanıt ver; sipariş/stok verisi hakkında ASLA tahmin yapma veya uydurma.

                Kurallar:
                - Sipariş durumu sorulursa get_order_status tool'unu çağır.
                - Stok/ürün durumu sorulursa check_stock tool'unu çağır.
                - Ürünün ne zaman stoğa gireceği sorulursa estimate_restock tool'unu çağır.
                - Kullanıcı bir siparişi iptal etmek isterse ÖNCE get_order_status ile durumunu \
                kontrol et. Sipariş 'pending' durumundaysa propose_cancellation tool'unu çağırarak \
                onay iste. propose_cancellation çağırmak siparişi GERÇEKTEN iptal ETMEZ, sadece \
                kullanıcıya onay için bir öneri sunar.
                - 'pending' olmayan bir siparişi iptal etmeyi ÖNERME; bunun yerine mevcut durumunu \
                açıklayan sade bir cevap ver.
                - Kısa, net ve Türkçe cevaplar ver.
                """;
    }

    private Map<String, Object> functionDeclarations() {
        return Map.of("functionDeclarations", List.of(
                toolDecl("get_order_status", "Belirli bir siparişin durumunu ve kalemlerini getirir.",
                        paramObject(Map.of("orderId", paramInt("Durumu sorulan siparişin numarası")), List.of("orderId"))),
                toolDecl("check_stock", "Bir ürünün güncel stok miktarını getirir.",
                        paramObject(Map.of("productId", paramInt("Stoğu sorulan ürünün kimliği")), List.of("productId"))),
                toolDecl("estimate_restock", "Bir ürünün ne zaman yeniden stoğa gireceğine dair kaba bir tahmin verir.",
                        paramObject(Map.of("productId", paramInt("Tahmini istenen ürünün kimliği")), List.of("productId"))),
                toolDecl("propose_cancellation",
                        "Kullanıcıya bir siparişi iptal etmesi için ONAY İSTER. Siparişi GERÇEKTEN iptal ETMEZ.",
                        paramObject(Map.of(
                                "orderId", paramInt("İptali önerilen siparişin numarası"),
                                "summary", paramString("Kullanıcıya gösterilecek kısa iptal özeti")
                        ), List.of("orderId", "summary")))
        ));
    }

    private Map<String, Object> toolDecl(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> decl = new LinkedHashMap<>();
        decl.put("name", name);
        decl.put("description", description);
        decl.put("parameters", parameters);
        return decl;
    }

    private Map<String, Object> paramObject(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "OBJECT", "properties", properties, "required", required);
    }

    private Map<String, Object> paramInt(String description) {
        return Map.of("type", "INTEGER", "description", description);
    }

    private Map<String, Object> paramString(String description) {
        return Map.of("type", "STRING", "description", description);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<?> handleInvalidRequest(InvalidRequestException e) {
        return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<?> handleUpstreamUnavailable(UpstreamUnavailableException e) {
        return ResponseEntity.status(502).body(Map.of("error", "upstream_unavailable"));
    }
}

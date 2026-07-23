package com.aurora.order.ai;

import com.aurora.order.exception.UpstreamUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

// Gemini generateContent REST API'sine ince bir sarmalayıcı.
// com.google.genai SDK'sı yerine doğrudan REST kullanılıyor: SDK Maven Central'da
// 1.63.0'a kadar ilerlemiş (spec'in andığı 1.0.0 çok eski), hızlı değişen bir yüzeye
// karşı sürüm uyumsuzluğu riski almaktansa, curl ile bizzat doğrulanmış REST
// sözleşmesine sadık kalmak daha güvenli.
@Component
public class GeminiClient {

    private final RestClient restClient;
    private final String model;
    private final String apiKey;

    public GeminiClient(@Value("${google.api-key}") String apiKey, @Value("${google.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);

        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(factory)
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Map<String, Object> generateContent(Map<String, Object> requestBody) {
        try {
            return restClient.post()
                    .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new UpstreamUnavailableException("gemini_unreachable", e);
        }
    }
}

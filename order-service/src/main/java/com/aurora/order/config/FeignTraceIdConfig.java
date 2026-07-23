package com.aurora.order.config;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// §10.5: ProductClient'ın attığı her istek (deduct/restore/getProduct), o anki isteğin
// trace id'sini X-Trace-Id başlığıyla product-service'e taşır — checkout'un iki servisteki
// logları tek bir id ile eşleşir.
@Configuration
public class FeignTraceIdConfig {

    @Bean
    public RequestInterceptor traceIdInterceptor() {
        return requestTemplate -> {
            String traceId = MDC.get(TraceIdFilter.MDC_KEY);
            if (traceId != null) {
                requestTemplate.header(TraceIdFilter.TRACE_ID_HEADER, traceId);
            }
        };
    }
}

package com.phonghub.adapter.out.ai;

import com.phonghub.application.exception.AiUnavailableException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Chuẩn hóa endpoint và RestClient giữ kết nối TLS cho các adapter mô hình AI. */
final class AiEndpointSupport {

    private AiEndpointSupport() {
    }

    static String resolveEndpoint(String baseUrl, String resourcePath) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AiUnavailableException();
        }
        String clean = baseUrl.strip().replaceAll("/+$", "");
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/v1") || lower.endsWith("/openai") || lower.endsWith("/v1beta")) {
            return clean + "/" + resourcePath;
        }
        return clean + "/v1/" + resourcePath;
    }

    static RestClient buildRestClient(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}

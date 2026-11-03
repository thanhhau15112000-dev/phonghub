package com.phonghub.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.application.exception.AiUnavailableException;
import com.phonghub.config.AiAssistantProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LiteLlmEmbeddingAdapterTest {
    @Test
    void disabledProviderDoesNotAttemptNetworkCall() {
        LiteLlmEmbeddingAdapter adapter = new LiteLlmEmbeddingAdapter(new AiAssistantProperties());
        assertThrows(AiUnavailableException.class, () -> adapter.embed("question"));
    }

    @Test
    void sendsOpenAiCompatibleEmbeddingRequestAndParsesVector() throws IOException {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        String vector = "0.25,".repeat(767) + "0.5";
        String responseBody = "{\"data\":[{\"embedding\":[" + vector + "]}]}";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AiAssistantProperties properties = enabledProperties(server.getAddress().getPort());
            double[] result = new LiteLlmEmbeddingAdapter(properties).embed("task: question answering | query: test");

            assertEquals("/v1/embeddings", requestPath.get());
            assertEquals("Bearer test-master-key", authorization.get());
            assertTrue(requestBody.get().contains("\"model\":\"gemini-faq-embedding\""));
            assertTrue(requestBody.get().contains("\"dimensions\":768"));
            assertEquals(768, result.length);
            assertEquals(0.25, result[0]);
            assertEquals(0.5, result[767]);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsUnexpectedVectorShapeWithoutExposingProviderError() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            byte[] bytes = "{\"data\":[{\"embedding\":[0.1]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AiAssistantProperties properties = enabledProperties(server.getAddress().getPort());
            assertThrows(AiUnavailableException.class,
                () -> new LiteLlmEmbeddingAdapter(properties).embed("query"));
        } finally {
            server.stop(0);
        }
    }

    private static AiAssistantProperties enabledProperties(int port) {
        AiAssistantProperties properties = new AiAssistantProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + port);
        properties.setApiKey("test-master-key");
        return properties;
    }
}

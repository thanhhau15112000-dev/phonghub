package com.phonghub.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.phonghub.application.exception.AiUnavailableException;
import com.phonghub.config.AiAssistantProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LiteLlmChatCompletionAdapterTest {
    private static final String OK_BODY = "{\"choices\":[{\"message\":{\"content\":\" Hóa đơn 3.500.000đ. \"}}]}";

    private final Logger logger = (Logger) LoggerFactory.getLogger(LiteLlmChatCompletionAdapter.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void attachLogCapture() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void cleanUp() {
        logger.detachAppender(logs);
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void disabledProviderDoesNotAttemptNetworkCall() {
        LiteLlmChatCompletionAdapter adapter = new LiteLlmChatCompletionAdapter(new AiAssistantProperties());
        assertThrows(AiUnavailableException.class, () -> adapter.complete("system", "prompt"));
    }

    @Test
    void sendsReasoningEffortByDefaultAndReturnsTrimmedAnswer() throws IOException {
        AiAssistantProperties properties = startServer(200, OK_BODY);

        assertEquals("Hóa đơn 3.500.000đ.", new LiteLlmChatCompletionAdapter(properties).complete("system", "prompt"));
        assertTrue(requestBody.get().contains("\"reasoning_effort\":\"low\""));
        assertTrue(requestBody.get().contains("\"model\":\"gemini-3.8-flash\""));
        assertTrue(requestBody.get().contains("\"stream\":false"));
        assertTrue(logs.list.isEmpty());
    }

    @Test
    void blankReasoningEffortIsNotSentSoModelsWithoutItStillWork() throws IOException {
        // #322: OpenAI gpt-4o-mini trả 400 khi nhận reasoning_effort
        AiAssistantProperties properties = startServer(200, OK_BODY);
        properties.setReasoningEffort(" ");

        new LiteLlmChatCompletionAdapter(properties).complete("system", "prompt");
        assertFalse(requestBody.get().contains("reasoning_effort"));
    }

    @Test
    void providerErrorIsLoggedWithStatusButWithoutPromptAnswerOrKey() throws IOException {
        AiAssistantProperties properties = startServer(400,
            "{\"error\":{\"message\":\"Unrecognized request argument supplied: reasoning_effort\"}}");

        assertThrows(AiUnavailableException.class,
            () -> new LiteLlmChatCompletionAdapter(properties).complete("system-bi-mat", "cau-hoi-rieng-tu"));

        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.contains("status=400"), message);
        assertTrue(message.contains("model=gemini-3.8-flash"), message);
        assertFalse(message.contains("cau-hoi-rieng-tu"));
        assertFalse(message.contains("system-bi-mat"));
        assertFalse(message.contains("test-master-key"));
        assertFalse(message.contains("Unrecognized"), "không ghi nội dung trả về của nhà cung cấp");
        assertNull(event.getThrowableProxy(), "không ghi stack trace có thể chứa nội dung trả về");
    }

    @Test
    void unreachableGatewayIsLogged() {
        AiAssistantProperties properties = enabledProperties(1);

        assertThrows(AiUnavailableException.class,
            () -> new LiteLlmChatCompletionAdapter(properties).complete("system", "prompt"));
        assertEquals(1, logs.list.size());
        assertTrue(logs.list.get(0).getFormattedMessage().contains("status=none"));
    }

    @Test
    void emptyAnswerIsLogged() throws IOException {
        AiAssistantProperties properties = startServer(200, "{\"choices\":[{\"message\":{\"content\":\"\"}}]}");

        assertThrows(AiUnavailableException.class,
            () -> new LiteLlmChatCompletionAdapter(properties).complete("system", "prompt"));
        assertEquals(1, logs.list.size());
        assertTrue(logs.list.get(0).getFormattedMessage().contains("rỗng"));
    }

    private AiAssistantProperties startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return enabledProperties(server.getAddress().getPort());
    }

    private static AiAssistantProperties enabledProperties(int port) {
        AiAssistantProperties properties = new AiAssistantProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + port);
        properties.setApiKey("test-master-key");
        properties.setTimeoutSeconds(2);
        return properties;
    }
}

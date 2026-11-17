package com.phonghub.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class FaqIndexConsistencyTest {
    @Test
    void generatedVectorsMatchTheCurrentFaqSourceAndRuntimeDimensions() throws Exception {
        byte[] faqBytes = Files.readAllBytes(Path.of("ops/faq/assistant-faq.vi.json"));
        String expectedSourceHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(faqBytes));
        ObjectMapper mapper = new ObjectMapper();
        JsonNode index;
        try (InputStream input = new ClassPathResource("faq/faq-index.json").getInputStream()) {
            index = mapper.readTree(input);
        }

        assertEquals("gemini-embedding-2", index.path("model").asText());
        assertEquals(768, index.path("dimensions").asInt());
        assertEquals(expectedSourceHash, index.path("faqSourceSha256").asText());
        assertEquals(13, index.path("entries").size());
        for (JsonNode entry : index.path("entries")) {
            assertEquals(768, entry.path("embedding").size(), entry.path("id").asText());
        }
    }
}

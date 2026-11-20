package com.phonghub.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.phonghub.application.exception.AiUnavailableException;
import org.junit.jupiter.api.Test;

class AiEndpointSupportTest {

    @Test
    void appendsV1ForHostOnlyBaseUrl() {
        assertEquals("http://litellm:4000/v1/chat/completions",
            AiEndpointSupport.resolveEndpoint("http://litellm:4000", "chat/completions"));
        assertEquals("https://api.openai.com/v1/embeddings",
            AiEndpointSupport.resolveEndpoint("https://api.openai.com/", "embeddings"));
    }

    @Test
    void avoidsDoubleV1ForVersionedOrOpenAiCompatBaseUrls() {
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            AiEndpointSupport.resolveEndpoint("https://generativelanguage.googleapis.com/v1beta/openai", "chat/completions"));
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/embeddings",
            AiEndpointSupport.resolveEndpoint("https://generativelanguage.googleapis.com/v1beta/openai/", "embeddings"));
        assertEquals("https://proxy.example.com/v1/chat/completions",
            AiEndpointSupport.resolveEndpoint("https://proxy.example.com/v1", "chat/completions"));
    }

    @Test
    void rejectsBlankBaseUrl() {
        assertThrows(AiUnavailableException.class, () -> AiEndpointSupport.resolveEndpoint(" ", "chat/completions"));
    }
}

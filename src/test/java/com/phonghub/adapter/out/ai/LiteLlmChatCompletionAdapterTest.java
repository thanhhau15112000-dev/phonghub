package com.phonghub.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.phonghub.application.exception.AiUnavailableException;
import com.phonghub.config.AiAssistantProperties;
import org.junit.jupiter.api.Test;

class LiteLlmChatCompletionAdapterTest {
    @Test
    void disabledProviderDoesNotAttemptNetworkCall() {
        LiteLlmChatCompletionAdapter adapter = new LiteLlmChatCompletionAdapter(new AiAssistantProperties());
        assertThrows(AiUnavailableException.class, () -> adapter.complete("system", "prompt"));
    }
}

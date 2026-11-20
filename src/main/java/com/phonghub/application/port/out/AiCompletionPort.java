package com.phonghub.application.port.out;

import java.util.List;

/** Sends a bounded, pre-scoped prompt to the configured language model. */
public interface AiCompletionPort {
    String complete(String systemPrompt, String userPrompt);

    default String complete(String systemPrompt, List<ChatTurn> history, String userPrompt) {
        return complete(systemPrompt, userPrompt);
    }

    record ChatTurn(String role, String content) {}
}

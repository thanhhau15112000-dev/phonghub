package com.phonghub.application.port.out;

/** Sends a bounded, pre-scoped prompt to the configured language model. */
public interface AiCompletionPort {
    String complete(String systemPrompt, String userPrompt);
}

package com.phonghub.application.port.out;

/** Creates a dense vector for semantic FAQ retrieval. */
public interface AiEmbeddingPort {
    double[] embed(String text);
}

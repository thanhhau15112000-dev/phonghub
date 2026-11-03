package com.phonghub.application.port.out;

import java.util.List;
import java.util.Set;

/** Retrieves the most relevant FAQ entries allowed for the current assistant task. */
public interface FaqSearchPort {
    List<FaqMatch> search(String query, Set<String> categories, int limit);

    record FaqMatch(
        String id,
        String category,
        String title,
        String question,
        String answer,
        String source,
        double relevance
    ) {}
}

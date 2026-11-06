package com.phonghub.adapter.out.ai;

import com.phonghub.application.exception.AiUnavailableException;
import com.phonghub.application.port.out.AiEmbeddingPort;
import com.phonghub.application.port.out.FaqSearchPort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Loads a pre-embedded FAQ index and ranks only the permitted categories in memory. */
public class FaqSearchAdapter implements FaqSearchPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(FaqSearchAdapter.class);
    private static final String QUERY_PREFIX = "task: question answering | query: ";

    private final AiEmbeddingPort embeddingPort;
    private final List<IndexedFaq> entries;
    private final int dimensions;
    private final double minimumSimilarity;
    private final Map<String, double[]> queryCache;

    public FaqSearchAdapter(Resource indexResource, AiEmbeddingPort embeddingPort, ObjectMapper objectMapper,
                            int expectedDimensions, double minimumSimilarity, int queryCacheSize) {
        this.embeddingPort = embeddingPort;
        this.dimensions = Math.max(1, expectedDimensions);
        this.minimumSimilarity = Math.max(-1, Math.min(1, minimumSimilarity));
        this.queryCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, double[]> eldest) {
                return size() > Math.max(1, queryCacheSize);
            }
        };
        this.entries = loadEntries(indexResource, objectMapper, this.dimensions);
    }

    /** Test seam for small deterministic indexes. */
    FaqSearchAdapter(List<IndexedFaq> entries, AiEmbeddingPort embeddingPort, int dimensions,
                     double minimumSimilarity, int queryCacheSize) {
        this.embeddingPort = embeddingPort;
        this.dimensions = Math.max(1, dimensions);
        this.minimumSimilarity = Math.max(-1, Math.min(1, minimumSimilarity));
        this.queryCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, double[]> eldest) {
                return size() > Math.max(1, queryCacheSize);
            }
        };
        this.entries = entries.stream()
            .filter(entry -> entry.embedding().length == this.dimensions && isFinite(entry.embedding()))
            .toList();
    }

    @Override
    public List<FaqMatch> search(String query, Set<String> categories, int limit) {
        if (query == null || query.isBlank() || categories == null || categories.isEmpty() || limit <= 0 || entries.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = categories.stream().map(FaqSearchAdapter::normalizeCategory).collect(Collectors.toSet());
        List<IndexedFaq> candidates = entries.stream()
            .filter(entry -> allowed.contains(normalizeCategory(entry.category())))
            .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        final double[] queryVector;
        try {
            queryVector = queryEmbedding(query);
        } catch (AiUnavailableException ex) {
            return List.of();
        }
        if (queryVector.length != dimensions || !isFinite(queryVector)) {
            return List.of();
        }

        return candidates.stream()
            .map(entry -> new ScoredFaq(entry, cosineSimilarity(queryVector, entry.embedding())))
            .filter(scored -> scored.score() >= minimumSimilarity)
            .sorted(Comparator.comparingDouble(ScoredFaq::score).reversed().thenComparing(scored -> scored.faq().id()))
            .limit(limit)
            .map(scored -> new FaqMatch(scored.faq().id(), scored.faq().category(), scored.faq().title(),
                scored.faq().question(), scored.faq().answer(), scored.faq().source(), scored.score()))
            .toList();
    }

    private double[] queryEmbedding(String query) {
        String normalizedQuery = query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String cacheKey = sha256(normalizedQuery);
        synchronized (queryCache) {
            double[] cached = queryCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        double[] embedded = embeddingPort.embed(QUERY_PREFIX + query.strip());
        if (embedded == null || embedded.length != dimensions || !isFinite(embedded)) {
            return new double[0];
        }
        double[] safeCopy = embedded.clone();
        synchronized (queryCache) {
            queryCache.put(cacheKey, safeCopy);
        }
        return safeCopy;
    }

    private static List<IndexedFaq> loadEntries(Resource resource, ObjectMapper mapper, int dimensions) {
        List<IndexedFaq> loaded = new ArrayList<>();
        try (InputStream input = resource.getInputStream()) {
            JsonNode root = mapper.readTree(input);
            if (!root.path("model").asText().equals("gemini-embedding-2")
                || root.path("dimensions").asInt(-1) != dimensions
                || !root.path("entries").isArray()) {
                LOGGER.warn("FAQ embedding index is incompatible with configured model or dimensions; FAQ retrieval disabled");
                return List.of();
            }
            for (JsonNode entry : root.path("entries")) {
                JsonNode vector = entry.path("embedding");
                if (!vector.isArray() || vector.size() != dimensions) {
                    continue;
                }
                double[] values = new double[dimensions];
                boolean valid = true;
                for (int i = 0; i < dimensions; i++) {
                    JsonNode value = vector.get(i);
                    if (value == null || !value.isNumber()) {
                        valid = false;
                        break;
                    }
                    values[i] = value.doubleValue();
                    valid &= Double.isFinite(values[i]);
                }
                if (!valid) {
                    continue;
                }
                loaded.add(new IndexedFaq(
                    entry.path("id").asText(), entry.path("category").asText(), entry.path("title").asText(),
                    entry.path("question").asText(), entry.path("answer").asText(), entry.path("source").asText(), values
                ));
            }
            if (loaded.isEmpty()) {
                LOGGER.warn("FAQ embedding index has no valid entries; FAQ retrieval disabled");
            }
            return List.copyOf(loaded);
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("FAQ embedding index could not be loaded; FAQ retrieval disabled");
            return List.of();
        }
    }

    private static double cosineSimilarity(double[] left, double[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static boolean isFinite(double[] vector) {
        for (double value : vector) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeCategory(String category) {
        return category == null ? "" : category.strip().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    record IndexedFaq(String id, String category, String title, String question, String answer, String source,
                      double[] embedding) {}

    private record ScoredFaq(IndexedFaq faq, double score) {}
}

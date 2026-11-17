package com.phonghub.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.out.AiEmbeddingPort;
import com.phonghub.application.port.out.FaqSearchPort.FaqMatch;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

class FaqSearchAdapterTest {
    @Test
    void ranksRelevantEntriesWithinAllowedCategoriesAndLimitsResults() {
        AiEmbeddingPort embeddingPort = mock(AiEmbeddingPort.class);
        when(embeddingPort.embed(anyString())).thenReturn(new double[] {1, 0});
        FaqSearchAdapter adapter = new FaqSearchAdapter(List.of(
            faq("invoice-1", "invoice", "Hóa đơn", new double[] {1, 0}),
            faq("invoice-2", "invoice", "Điện nước", new double[] {0.8, 0.2}),
            faq("contract-1", "contract", "Hợp đồng", new double[] {0.99, 0.01})
        ), embeddingPort, 2, 0.65, 10);

        List<FaqMatch> result = adapter.search("Hóa đơn so sánh kỳ trước?", Set.of("invoice"), 1);

        assertEquals(1, result.size());
        assertEquals("invoice-1", result.getFirst().id());
        assertTrue(result.getFirst().relevance() > 0.99);
        verify(embeddingPort).embed("task: question answering | query: Hóa đơn so sánh kỳ trước?");
    }

    @Test
    void filtersLowSimilarityAndCachesTheQueryVectorWithoutKeepingQuestionText() {
        AiEmbeddingPort embeddingPort = mock(AiEmbeddingPort.class);
        when(embeddingPort.embed(anyString())).thenReturn(new double[] {1, 0});
        FaqSearchAdapter adapter = new FaqSearchAdapter(List.of(
            faq("contract-1", "contract", "Hợp đồng", new double[] {0, 1})
        ), embeddingPort, 2, 0.65, 10);

        assertTrue(adapter.search("How many rooms?", Set.of("contract"), 3).isEmpty());
        assertTrue(adapter.search("How many rooms?", Set.of("contract"), 3).isEmpty());

        verify(embeddingPort, times(1)).embed(anyString());
    }

    @Test
    void skipsEmbeddingForEmptyQueryCategoryOrIndex() {
        AiEmbeddingPort embeddingPort = mock(AiEmbeddingPort.class);
        FaqSearchAdapter adapter = new FaqSearchAdapter(List.of(), embeddingPort, 2, 0.65, 10);

        assertTrue(adapter.search("question", Set.of("invoice"), 3).isEmpty());
        assertTrue(adapter.search("question", Set.of(), 3).isEmpty());
        assertTrue(adapter.search("  ", Set.of("invoice"), 3).isEmpty());

        verify(embeddingPort, times(0)).embed(anyString());
    }

    @Test
    void loadsTheGeneratedIndexFromApplicationResources() {
        AiEmbeddingPort embeddingPort = mock(AiEmbeddingPort.class);
        when(embeddingPort.embed(anyString())).thenReturn(new double[768]);
        FaqSearchAdapter adapter = new FaqSearchAdapter(new ClassPathResource("faq/faq-index.json"),
            embeddingPort, new ObjectMapper(), 768, -1, 10);

        List<FaqMatch> result = adapter.search("test query", Set.of("invoice"), 3);

        assertEquals(3, result.size());
    }

    @Test
    void missingResourceDisablesFaqSearchWithoutBlockingTheApplication() {
        AiEmbeddingPort embeddingPort = mock(AiEmbeddingPort.class);
        FaqSearchAdapter adapter = new FaqSearchAdapter(new ClassPathResource("faq/missing-index.json"),
            embeddingPort, new ObjectMapper(), 768, 0.65, 10);

        assertTrue(adapter.search("question", Set.of("invoice"), 3).isEmpty());

        verify(embeddingPort, times(0)).embed(anyString());
    }

    private static FaqSearchAdapter.IndexedFaq faq(String id, String category, String title, double[] vector) {
        return new FaqSearchAdapter.IndexedFaq(id, category, title, "Question", "Answer", "Source", vector);
    }
}

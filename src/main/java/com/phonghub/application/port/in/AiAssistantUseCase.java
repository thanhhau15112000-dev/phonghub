package com.phonghub.application.port.in;

import com.phonghub.domain.model.Property;
import java.util.List;
import java.util.UUID;

public interface AiAssistantUseCase {
    AiResponse analyzeInvoice(UUID invoiceId);
    AiResponse askAboutInvoice(UUID invoiceId, String question);
    AiResponse askAboutContract(UUID contractId, String question);
    AiResponse askOperations(UUID propertyId, String question);
    List<Property> listOperationsProperties();
    AiWidgetContext getWidgetContext();
    AiResponse askAssistant(String contextType, UUID targetId, String question);

    default AiResponse askAssistant(String contextType, UUID targetId, String question, List<ChatMessageInput> history) {
        return askAssistant(contextType, targetId, question);
    }

    record ChatMessageInput(String role, String content) {}

    record ContextOption(String contextType, UUID targetId, String label) {}

    record QuickPrompt(String label, String question, String contextType, UUID targetId) {}

    record AiWidgetContext(
        String role,
        String roleLabel,
        String scopeNote,
        List<ContextOption> contexts,
        List<QuickPrompt> quickPrompts
    ) {
        public AiWidgetContext {
            contexts = List.copyOf(contexts);
            quickPrompts = List.copyOf(quickPrompts);
        }
    }

    record AiResponse(String answer, List<Source> sources) {
        public AiResponse {
            sources = List.copyOf(sources);
        }
    }

    record Source(String label) {}
}

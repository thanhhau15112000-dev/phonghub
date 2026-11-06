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

    record AiResponse(String answer, List<Source> sources) {
        public AiResponse {
            sources = List.copyOf(sources);
        }
    }

    record Source(String label) {}
}

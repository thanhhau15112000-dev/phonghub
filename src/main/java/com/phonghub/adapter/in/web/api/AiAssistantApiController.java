package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.AiAssistantUseCase;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistantApiController {
    private final AiAssistantUseCase assistant;

    public AiAssistantApiController(AiAssistantUseCase assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/invoices/{id}/analysis")
    public AiAssistantUseCase.AiResponse analyzeInvoice(@PathVariable UUID id) {
        return assistant.analyzeInvoice(id);
    }

    @PostMapping("/invoices/{id}/ask")
    public AiAssistantUseCase.AiResponse askAboutInvoice(
        @PathVariable UUID id,
        @RequestBody QuestionRequest request
    ) {
        return assistant.askAboutInvoice(id, request == null ? null : request.question());
    }

    @PostMapping("/contracts/{id}/ask")
    public AiAssistantUseCase.AiResponse askAboutContract(
        @PathVariable UUID id,
        @RequestBody QuestionRequest request
    ) {
        return assistant.askAboutContract(id, request == null ? null : request.question());
    }

    @PostMapping("/operations/ask")
    public AiAssistantUseCase.AiResponse askOperations(@RequestBody OperationsQuestionRequest request) {
        return assistant.askOperations(request == null ? null : request.propertyId(),
            request == null ? null : request.question());
    }

    public record QuestionRequest(String question) {}
    public record OperationsQuestionRequest(UUID propertyId, String question) {}
}

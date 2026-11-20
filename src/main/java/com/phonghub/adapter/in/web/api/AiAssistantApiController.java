package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.AiAssistantUseCase;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/widget-context")
    public AiAssistantUseCase.AiWidgetContext widgetContext() {
        return assistant.getWidgetContext();
    }

    @PostMapping("/ask")
    public AiAssistantUseCase.AiResponse askGeneral(@RequestBody(required = false) GeneralQuestionRequest request) {
        return assistant.askAssistant(
            request == null ? null : request.contextType(),
            request == null ? null : request.targetId(),
            request == null ? null : request.question()
        );
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
    public record GeneralQuestionRequest(String contextType, UUID targetId, String question) {}
}

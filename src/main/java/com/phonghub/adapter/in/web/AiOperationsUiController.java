package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.AiAssistantUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ai/operations")
public class AiOperationsUiController {
    private final AiAssistantUseCase assistant;

    public AiOperationsUiController(AiAssistantUseCase assistant) {
        this.assistant = assistant;
    }

    @GetMapping
    public String operationsAssistant(Model model) {
        model.addAttribute("properties", assistant.listOperationsProperties());
        return "ai/operations";
    }
}

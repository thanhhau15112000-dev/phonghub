package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.LateFeePolicyUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.LateFeePolicy;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Cấu hình phạt trả trễ của nhà trọ: chỉ chủ nhà trọ và quản trị viên. */
@Controller
@RequestMapping("/properties/{propertyId}/late-fee")
public class LateFeePolicyUiController {

    private final LateFeePolicyUseCase policyUseCase;
    private final PropertyUseCase propertyUseCase;

    public LateFeePolicyUiController(LateFeePolicyUseCase policyUseCase, PropertyUseCase propertyUseCase) {
        this.policyUseCase = policyUseCase;
        this.propertyUseCase = propertyUseCase;
    }

    @GetMapping
    public String page(@PathVariable UUID propertyId, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("policy", policyUseCase.getPolicy(propertyId));
            model.addAttribute("property", propertyUseCase.getProperty(propertyId));
            return "properties/late-fee";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties/" + propertyId;
        }
    }

    @PostMapping
    public String save(
        @PathVariable UUID propertyId,
        @RequestParam(defaultValue = "false") boolean enabled,
        @RequestParam(defaultValue = "PERCENT") String feeType,
        @RequestParam(required = false) BigDecimal feeValue,
        @RequestParam(defaultValue = "0") int graceDays,
        @RequestParam(required = false) BigDecimal capAmount,
        RedirectAttributes redirectAttributes
    ) {
        try {
            policyUseCase.updatePolicy(new LateFeePolicyUseCase.UpdateCommand(
                propertyId, enabled, LateFeePolicy.FeeType.valueOf(feeType), feeValue, graceDays, capAmount));
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu cấu hình phạt trả trễ.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + propertyId + "/late-fee";
    }
}

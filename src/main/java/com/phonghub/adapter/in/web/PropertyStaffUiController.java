package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.PropertyStaffUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang phân công nhân sự của nhà trọ: chỉ chủ nhà trọ và quản trị viên. */
@Controller
@RequestMapping("/properties/{propertyId}/staff")
public class PropertyStaffUiController {

    private final PropertyStaffUseCase staffUseCase;
    private final PropertyUseCase propertyUseCase;
    private final CurrentUserPort currentUserPort;

    public PropertyStaffUiController(
        PropertyStaffUseCase staffUseCase,
        PropertyUseCase propertyUseCase,
        CurrentUserPort currentUserPort
    ) {
        this.staffUseCase = staffUseCase;
        this.propertyUseCase = propertyUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping
    public String page(@PathVariable UUID propertyId, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("members", staffUseCase.listMembers(propertyId));
            model.addAttribute("property", propertyUseCase.getProperty(propertyId));
            model.addAttribute("currentUser", currentUserPort.getCurrentUser());
            return "properties/staff";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties/" + propertyId;
        }
    }

    @PostMapping
    public String assign(
        @PathVariable UUID propertyId,
        @RequestParam String usernameOrEmail,
        @RequestParam(defaultValue = "false") boolean canCollectPayment,
        @RequestParam(defaultValue = "false") boolean canManageContracts,
        @RequestParam(defaultValue = "false") boolean canViewDocuments,
        @RequestParam(defaultValue = "false") boolean canManageRoomsAndAssets,
        RedirectAttributes redirectAttributes
    ) {
        try {
            staffUseCase.assign(new PropertyStaffUseCase.AssignCommand(
                propertyId, usernameOrEmail, canCollectPayment, canManageContracts, canViewDocuments,
                canManageRoomsAndAssets));
            redirectAttributes.addFlashAttribute("successMessage", "Đã phân công nhân sự vào nhà trọ.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + propertyId + "/staff";
    }

    @PostMapping("/{userId}/permissions")
    public String permissions(
        @PathVariable UUID propertyId,
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "false") boolean canCollectPayment,
        @RequestParam(defaultValue = "false") boolean canManageContracts,
        @RequestParam(defaultValue = "false") boolean canViewDocuments,
        @RequestParam(defaultValue = "false") boolean canManageRoomsAndAssets,
        RedirectAttributes redirectAttributes
    ) {
        try {
            staffUseCase.updatePermissions(propertyId, userId, canCollectPayment, canManageContracts,
                canViewDocuments, canManageRoomsAndAssets);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật quyền.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + propertyId + "/staff";
    }

    @PostMapping("/{userId}/remove")
    public String remove(@PathVariable UUID propertyId, @PathVariable UUID userId, RedirectAttributes redirectAttributes) {
        try {
            staffUseCase.unassign(propertyId, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gỡ nhân sự khỏi nhà trọ.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + propertyId + "/staff";
    }
}

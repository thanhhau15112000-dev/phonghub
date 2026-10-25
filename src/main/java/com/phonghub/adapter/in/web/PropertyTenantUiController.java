package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Tenant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang quản lý người thuê của một nhà trọ. Danh sách chỉ hiện số CCCD đã che; xem đầy đủ qua trang giấy tờ. */
@Controller
@RequestMapping("/properties/{propertyId}/tenants")
public class PropertyTenantUiController {

    private final TenantUseCase tenantUseCase;
    private final PropertyUseCase propertyUseCase;
    private final CurrentUserPort currentUserPort;

    public PropertyTenantUiController(
        TenantUseCase tenantUseCase,
        PropertyUseCase propertyUseCase,
        CurrentUserPort currentUserPort
    ) {
        this.tenantUseCase = tenantUseCase;
        this.propertyUseCase = propertyUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping
    public String list(
        @PathVariable UUID propertyId,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "false") boolean includeHidden,
        @RequestParam(defaultValue = "0") int page,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        try {
            TenantUseCase.TenantPage result = tenantUseCase.listTenants(propertyId, q, includeHidden, page, 20);
            Property property = propertyUseCase.getProperty(propertyId);
            model.addAttribute("currentUser", currentUserPort.getCurrentUser());
            model.addAttribute("property", property);
            model.addAttribute("result", result);
            model.addAttribute("q", q == null ? "" : q);
            model.addAttribute("includeHidden", includeHidden);
            return "tenants/list";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties/" + propertyId;
        }
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID propertyId, Model model, RedirectAttributes redirectAttributes) {
        try {
            tenantUseCase.listTenants(propertyId, null, false, 0, 1);
            model.addAttribute("currentUser", currentUserPort.getCurrentUser());
            model.addAttribute("property", propertyUseCase.getProperty(propertyId));
            model.addAttribute("tenant", null);
            java.util.Set<UUID> busyTenantIds = new java.util.HashSet<>();
            List<Tenant> tenantProfiles = selectableTenantProfiles(propertyId, busyTenantIds);
            model.addAttribute("tenantChoices",
                TenantChoice.merge(tenantProfiles, busyTenantIds, List.of()));
            return "tenants/form";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties/" + propertyId;
        }
    }

    @PostMapping
    public String add(
        @PathVariable UUID propertyId,
        @RequestParam String fullName,
        @RequestParam(required = false) String identityCardNumber,
        @RequestParam(required = false) String phone,
        @RequestParam(required = false) String email,
        @RequestParam(required = false) String permanentAddress,
        @RequestParam(required = false) UUID tenantProfileId,
        @RequestParam(required = false) UUID tenantUserId,
        RedirectAttributes redirectAttributes
    ) {
        try {
            if (tenantProfileId != null || tenantUserId != null) {
                TenantUseCase.PropertyTenantView added = tenantUseCase.addTenant(new TenantUseCase.AddTenantCommand(
                    propertyId, tenantProfileId, tenantUserId, fullName, identityCardNumber, phone, email,
                    permanentAddress));
                redirectAttributes.addFlashAttribute("successMessage", added.tenant().userId() == null
                    ? "Đã thêm người thuê. Hồ sơ chưa có tài khoản, hãy dùng nút Cấp tài khoản."
                    : "Đã thêm người thuê.");
                return "redirect:/properties/" + propertyId + "/tenants";
            }
            TenantUseCase.TenantAccountResult result = tenantUseCase.createTenantAccount(
                new TenantUseCase.CreateTenantAccountCommand(propertyId, fullName, identityCardNumber, phone, email,
                    permanentAddress));
            redirectAttributes.addFlashAttribute("tenantAccountResult", result);
            redirectAttributes.addFlashAttribute("successMessage", result.accountCreated()
                ? "Đã tạo hồ sơ và tài khoản người thuê. Mật khẩu tạm chỉ hiển thị lần này."
                : "Đã liên kết hồ sơ với tài khoản người thuê hiện có.");
            return "redirect:/properties/" + propertyId + "/tenants";
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties/" + propertyId + "/tenants/new";
        }
    }

    @PostMapping("/{tenantId}/issue-account")
    public String issueAccount(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @RequestParam String email,
        RedirectAttributes redirectAttributes
    ) {
        try {
            TenantUseCase.TenantAccountResult result = tenantUseCase.issueTenantAccount(
                new TenantUseCase.IssueTenantAccountCommand(propertyId, tenantId, email));
            redirectAttributes.addFlashAttribute("tenantAccountResult", result);
            redirectAttributes.addFlashAttribute("successMessage", result.accountCreated()
                ? "Đã cấp tài khoản người thuê. Mật khẩu tạm chỉ hiển thị lần này."
                : "Đã liên kết tài khoản người thuê hiện có.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + propertyId + "/tenants";
    }

    @GetMapping("/{tenantId}/edit")
    public String editForm(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        try {
            TenantUseCase.PropertyTenantView view = tenantUseCase.getPropertyTenant(propertyId, tenantId);
            model.addAttribute("currentUser", currentUserPort.getCurrentUser());
            model.addAttribute("property", propertyUseCase.getProperty(propertyId));
            model.addAttribute("tenant", view.tenant());
            return "tenants/form";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties/" + propertyId + "/tenants";
        }
    }

    @PostMapping("/{tenantId}/link-account")
    public String linkAccount(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @RequestParam String email,
        RedirectAttributes redirectAttributes
    ) {
        try {
            tenantUseCase.getPropertyTenant(propertyId, tenantId);
            tenantUseCase.linkAccountByEmail(tenantId, email);
            redirectAttributes.addFlashAttribute("successMessage", "Đã liên kết tài khoản người thuê theo email.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + propertyId + "/tenants/" + tenantId + "/edit";
    }

    private List<Tenant> selectableTenantProfiles(UUID targetPropertyId, java.util.Set<UUID> busyTenantIds) {
        java.util.Set<UUID> alreadyLinked = tenantUseCase.listTenants(targetPropertyId, null, true, 0, 50).items()
            .stream().map(view -> view.tenant().id()).collect(java.util.stream.Collectors.toSet());
        java.util.Map<UUID, Tenant> choices = new java.util.LinkedHashMap<>();
        for (Property property : propertyUseCase.listAccessibleProperties()) {
            if (property.id().equals(targetPropertyId)) continue;
            try {
                for (TenantUseCase.PropertyTenantView view
                    : tenantUseCase.listTenants(property.id(), null, false, 0, 50).items()) {
                    if (!alreadyLinked.contains(view.tenant().id())) {
                        choices.putIfAbsent(view.tenant().id(), view.tenant());
                        if (view.hasActiveContract()) {
                            busyTenantIds.add(view.tenant().id());
                        }
                    }
                }
            } catch (DomainException ignored) {
                // Only include profiles from properties this user can manage.
            }
        }
        return choices.values().stream()
            .sorted(java.util.Comparator.comparing(Tenant::fullName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @PostMapping("/{tenantId}/edit")
    public String edit(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @RequestParam String fullName,
        @RequestParam(required = false) String identityCardNumber,
        @RequestParam String phone,
        @RequestParam(required = false) String email,
        @RequestParam(required = false) String permanentAddress,
        RedirectAttributes redirectAttributes
    ) {
        try {
            // Chỉ cho sửa tenant thuộc đúng nhà trọ trên đường dẫn.
            tenantUseCase.getPropertyTenant(propertyId, tenantId);
            tenantUseCase.updateTenant(new TenantUseCase.UpdateTenantCommand(
                tenantId, fullName, identityCardNumber, phone, email, permanentAddress));
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật thông tin người thuê.");
            return "redirect:/properties/" + propertyId + "/tenants";
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties/" + propertyId + "/tenants/" + tenantId + "/edit";
        }
    }

    @PostMapping("/{tenantId}/hide")
    public String hide(@PathVariable UUID propertyId, @PathVariable UUID tenantId, RedirectAttributes redirectAttributes) {
        try {
            tenantUseCase.hideTenant(propertyId, tenantId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ẩn người thuê khỏi danh sách.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + propertyId + "/tenants";
    }

    @PostMapping("/{tenantId}/restore")
    public String restore(@PathVariable UUID propertyId, @PathVariable UUID tenantId, RedirectAttributes redirectAttributes) {
        try {
            tenantUseCase.restoreTenant(propertyId, tenantId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã khôi phục người thuê.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + propertyId + "/tenants?includeHidden=true";
    }
}

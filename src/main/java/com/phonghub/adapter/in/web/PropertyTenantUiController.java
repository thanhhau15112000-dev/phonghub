package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
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
    private final UserRepositoryPort userRepository;
    private final TenantRepositoryPort tenantRepository;

    public PropertyTenantUiController(
        TenantUseCase tenantUseCase,
        PropertyUseCase propertyUseCase,
        CurrentUserPort currentUserPort,
        UserRepositoryPort userRepository,
        TenantRepositoryPort tenantRepository
    ) {
        this.tenantUseCase = tenantUseCase;
        this.propertyUseCase = propertyUseCase;
        this.currentUserPort = currentUserPort;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
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
                TenantChoice.merge(tenantProfiles, busyTenantIds, activeTenantAccounts(tenantProfiles)));
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
        @RequestParam(defaultValue = "false") boolean createAccount,
        RedirectAttributes redirectAttributes
    ) {
        try {
            TenantUseCase.PropertyTenantView added = tenantUseCase.addTenant(new TenantUseCase.AddTenantCommand(
                propertyId, tenantProfileId, tenantUserId, fullName, identityCardNumber, phone, email, permanentAddress));
            if (!createAccount) {
                redirectAttributes.addFlashAttribute("successMessage", "Đã thêm người thuê.");
                return "redirect:/properties/" + propertyId + "/tenants";
            }
            // Hồ sơ đã lưu; cấp tài khoản lỗi thì đưa sang trang sửa để thử lại, không mất hồ sơ vừa thêm
            try {
                redirectAttributes.addFlashAttribute("successMessage", "Đã thêm người thuê. "
                    + accountMessage(tenantUseCase.provisionAccount(added.tenant().id(), email)));
                return "redirect:/properties/" + propertyId + "/tenants";
            } catch (DomainException | IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Đã thêm người thuê nhưng chưa cấp được tài khoản: " + ex.getMessage());
                return "redirect:/properties/" + propertyId + "/tenants/" + added.tenant().id() + "/edit";
            }
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties/" + propertyId + "/tenants/new";
        }
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

    @PostMapping("/{tenantId}/create-account")
    public String createAccount(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @RequestParam String email,
        RedirectAttributes redirectAttributes
    ) {
        try {
            tenantUseCase.getPropertyTenant(propertyId, tenantId);
            redirectAttributes.addFlashAttribute("successMessage",
                accountMessage(tenantUseCase.provisionAccount(tenantId, email)));
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + propertyId + "/tenants/" + tenantId + "/edit";
    }

    private static String accountMessage(TenantUseCase.AccountProvisioning account) {
        return account.temporaryPassword() == null
            ? "Đã liên kết tài khoản TENANT có sẵn " + account.username() + "."
            : "Đã cấp tài khoản TENANT " + account.username() + " với mật khẩu tạm " + account.temporaryPassword()
                + " (chỉ hiển thị một lần, hãy gửi cho người thuê).";
    }

    private List<User> activeTenantAccounts(List<Tenant> profiles) {
        java.util.Set<UUID> visibleLinkedUsers = profiles.stream().map(Tenant::userId)
            .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return userRepository.findByRole(UserRole.TENANT).stream()
            .filter(user -> user.status() == User.UserStatus.ACTIVE)
            .filter(user -> tenantRepository.findByUserId(user.id()).isEmpty() || visibleLinkedUsers.contains(user.id()))
            .sorted(java.util.Comparator.comparing(User::fullName, String.CASE_INSENSITIVE_ORDER))
            .toList();
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

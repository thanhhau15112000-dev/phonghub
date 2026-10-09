package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.HandoverUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyHouseRules;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/contracts/{contractId}/handover")
public class HandoverUiController {

    private final HandoverUseCase handoverUseCase;
    private final ContractUseCase contractUseCase;
    private final PropertyUseCase propertyUseCase;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;

    public HandoverUiController(
        HandoverUseCase handoverUseCase,
        ContractUseCase contractUseCase,
        PropertyUseCase propertyUseCase,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort
    ) {
        this.handoverUseCase = handoverUseCase;
        this.contractUseCase = contractUseCase;
        this.propertyUseCase = propertyUseCase;
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping
    public String page(@PathVariable UUID contractId, Model model, RedirectAttributes redirectAttributes) {
        CurrentUser user = currentUserPort.getCurrentUser();
        try {
            Contract contract = contractUseCase.getContract(contractId);
            Property property = propertyUseCase.getProperty(contract.getPropertyId());
            boolean canManage = user != null && (user.role() == UserRole.ADMIN || user.role() == UserRole.STAFF
                || (user.role() == UserRole.OWNER && user.id().equals(property.ownerId())));
            boolean primaryTenant = false;
            if (user != null && user.role() == UserRole.TENANT) {
                Tenant tenant = tenantRepository.findByUserId(user.id()).orElse(null);
                primaryTenant = tenant != null && tenant.id().equals(contract.getPrimaryTenantId());
            }
            HandoverUseCase.HandoverDetails details = null;
            try {
                details = handoverUseCase.get(contractId);
            } catch (com.phonghub.domain.exception.HandoverNotFoundException ignored) {
                // chưa có biên bản: hiển thị form tạo cho người quản lý
            }
            PropertyHouseRules rules = canManage || primaryTenant
                ? handoverUseCase.getHouseRules(contract.getPropertyId()) : null;

            model.addAttribute("currentUser", user);
            model.addAttribute("contract", contract);
            model.addAttribute("property", property);
            model.addAttribute("details", details);
            model.addAttribute("canManage", canManage);
            model.addAttribute("primaryTenant", primaryTenant);
            model.addAttribute("houseRules", rules);
            model.addAttribute("photoKinds", HandoverPhotoKind.values());
            return "contracts/handover";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/contracts";
        }
    }

    @PostMapping
    public String create(
        @PathVariable UUID contractId,
        @RequestParam BigDecimal initialElectricMeter,
        @RequestParam BigDecimal initialWaterMeter,
        @RequestParam String roomCondition,
        @RequestParam(required = false) String keysProvided,
        RedirectAttributes redirectAttributes
    ) {
        return handle(contractId, redirectAttributes, "Đã tạo biên bản check-in (bản nháp).", () ->
            handoverUseCase.create(new HandoverUseCase.HandoverCommand(contractId, initialElectricMeter,
                initialWaterMeter, roomCondition, keysProvided)));
    }

    @PostMapping("/update")
    public String update(
        @PathVariable UUID contractId,
        @RequestParam BigDecimal initialElectricMeter,
        @RequestParam BigDecimal initialWaterMeter,
        @RequestParam String roomCondition,
        @RequestParam(required = false) String keysProvided,
        RedirectAttributes redirectAttributes
    ) {
        return handle(contractId, redirectAttributes, "Đã cập nhật biên bản.", () ->
            handoverUseCase.update(new HandoverUseCase.HandoverCommand(contractId, initialElectricMeter,
                initialWaterMeter, roomCondition, keysProvided)));
    }

    @PostMapping("/photos")
    public String addPhoto(
        @PathVariable UUID contractId,
        @RequestParam HandoverPhotoKind kind,
        @RequestParam("file") MultipartFile file,
        RedirectAttributes redirectAttributes
    ) {
        return handle(contractId, redirectAttributes, "Đã thêm ảnh.", () -> {
            try {
                handoverUseCase.addPhoto(contractId, kind, file.getContentType(), file.getBytes());
            } catch (IOException ex) {
                throw new IllegalArgumentException("Không đọc được tệp ảnh", ex);
            }
        });
    }

    @PostMapping("/photos/{photoId}/delete")
    public String removePhoto(
        @PathVariable UUID contractId,
        @PathVariable UUID photoId,
        RedirectAttributes redirectAttributes
    ) {
        return handle(contractId, redirectAttributes, "Đã xóa ảnh.", () ->
            handoverUseCase.removePhoto(contractId, photoId));
    }

    @GetMapping("/photos/{photoId}")
    public ResponseEntity<Void> photo(@PathVariable UUID contractId, @PathVariable UUID photoId) {
        URI target = handoverUseCase.photoSignedUrl(contractId, photoId);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/send")
    public String send(@PathVariable UUID contractId, RedirectAttributes redirectAttributes) {
        return handle(contractId, redirectAttributes, "Đã gửi biên bản cho khách thuê.", () ->
            handoverUseCase.send(contractId));
    }

    @PostMapping("/confirm")
    public String confirm(@PathVariable UUID contractId, RedirectAttributes redirectAttributes) {
        return handle(contractId, redirectAttributes, "Đã xác nhận biên bản. Chờ chủ trọ duyệt.", () ->
            handoverUseCase.confirm(contractId));
    }

    @PostMapping("/approve")
    public String approve(@PathVariable UUID contractId, RedirectAttributes redirectAttributes) {
        return handle(contractId, redirectAttributes, "Đã duyệt biên bản check-in.", () ->
            handoverUseCase.approve(contractId));
    }

    @PostMapping("/reject")
    public String reject(
        @PathVariable UUID contractId,
        @RequestParam String reason,
        RedirectAttributes redirectAttributes
    ) {
        return handle(contractId, redirectAttributes, "Đã từ chối biên bản.", () ->
            handoverUseCase.reject(contractId, reason));
    }

    @PostMapping("/reopen")
    public String reopen(@PathVariable UUID contractId, RedirectAttributes redirectAttributes) {
        return handle(contractId, redirectAttributes, "Đã mở lại biên bản để chỉnh sửa.", () ->
            handoverUseCase.reopen(contractId));
    }

    @PostMapping("/house-rules")
    public String saveHouseRules(
        @PathVariable UUID contractId,
        @RequestParam String content,
        RedirectAttributes redirectAttributes
    ) {
        return handle(contractId, redirectAttributes, "Đã lưu nội quy của nhà trọ.", () -> {
            Contract contract = contractUseCase.getContract(contractId);
            handoverUseCase.saveHouseRules(contract.getPropertyId(), content);
        });
    }

    private String handle(UUID contractId, RedirectAttributes redirectAttributes, String success, Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", success);
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + contractId + "/handover";
    }
}

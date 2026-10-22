package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.CheckOutSettlementUseCase;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.HandoverUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyHouseRules;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
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

/** Trang biên bản bàn giao: {@code kind} là "handover" (check-in) hoặc "check-out". */
@Controller
@RequestMapping("/contracts/{contractId}/{kind:handover|check-out}")
public class HandoverUiController {

    private final HandoverUseCase handoverUseCase;
    private final ContractUseCase contractUseCase;
    private final PropertyUseCase propertyUseCase;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;
    private final CheckOutSettlementUseCase settlementUseCase;
    private final boolean skipEnabled;

    public HandoverUiController(
        HandoverUseCase handoverUseCase,
        ContractUseCase contractUseCase,
        PropertyUseCase propertyUseCase,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        CheckOutSettlementUseCase settlementUseCase,
        @Value("${phonghub.handover.skip-enabled:false}") boolean skipEnabled
    ) {
        this.handoverUseCase = handoverUseCase;
        this.contractUseCase = contractUseCase;
        this.propertyUseCase = propertyUseCase;
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
        this.settlementUseCase = settlementUseCase;
        this.skipEnabled = skipEnabled;
    }

    private static HandoverType typeOf(String kind) {
        return "check-out".equals(kind) ? HandoverType.CHECK_OUT : HandoverType.CHECK_IN;
    }

    @GetMapping
    public String page(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        CurrentUser user = currentUserPort.getCurrentUser();
        HandoverType type = typeOf(kind);
        try {
            Contract contract = contractUseCase.getContract(contractId);
            Property property = propertyUseCase.getProperty(contract.getPropertyId());
            boolean canManage = user != null && (user.role() == UserRole.ADMIN || user.role() == UserRole.STAFF
                || (user.role() == UserRole.OWNER && user.id().equals(property.ownerId())));
            boolean ownerOrAdmin = user != null && (user.role() == UserRole.ADMIN
                || (user.role() == UserRole.OWNER && user.id().equals(property.ownerId())));
            boolean primaryTenant = false;
            if (user != null && user.role() == UserRole.TENANT) {
                Tenant tenant = tenantRepository.findByUserId(user.id()).orElse(null);
                primaryTenant = tenant != null && tenant.id().equals(contract.getPrimaryTenantId());
            }
            HandoverUseCase.HandoverDetails details = null;
            try {
                details = handoverUseCase.get(type, contractId);
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
            model.addAttribute("ownerOrAdmin", ownerOrAdmin);
            model.addAttribute("primaryTenant", primaryTenant);
            model.addAttribute("houseRules", rules);
            model.addAttribute("photoKinds", HandoverPhotoKind.values());
            model.addAttribute("assetStatuses", AssetStatus.values());
            model.addAttribute("kind", kind);
            model.addAttribute("canSkip", skipEnabled && (type == HandoverType.CHECK_OUT ? ownerOrAdmin : canManage)
                && (details == null || !"APPROVED".equals(details.handover().status().name())));
            model.addAttribute("checkOut", type == HandoverType.CHECK_OUT);
            // Quyết toán cọc: chỉ trang check-out, chỉ người xem được số liệu tiền của hợp đồng
            model.addAttribute("settlement", type == HandoverType.CHECK_OUT && details != null
                ? settlementUseCase.settlement(contractId).orElse(null) : null);
            model.addAttribute("base", "/contracts/" + contractId + "/" + kind);
            return "contracts/handover";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/contracts";
        }
    }

    @PostMapping
    public String create(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @RequestParam BigDecimal initialElectricMeter,
        @RequestParam BigDecimal initialWaterMeter,
        @RequestParam String roomCondition,
        @RequestParam(required = false) String keysProvided,
        RedirectAttributes redirectAttributes
    ) {
        return handle(contractId, kind, redirectAttributes, "Đã tạo biên bản (bản nháp).", () ->
            handoverUseCase.create(typeOf(kind), new HandoverUseCase.HandoverCommand(contractId,
                initialElectricMeter, initialWaterMeter, roomCondition, keysProvided)));
    }

    @PostMapping("/update")
    public String update(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @RequestParam BigDecimal initialElectricMeter,
        @RequestParam BigDecimal initialWaterMeter,
        @RequestParam String roomCondition,
        @RequestParam(required = false) String keysProvided,
        RedirectAttributes redirectAttributes
    ) {
        return handle(contractId, kind, redirectAttributes, "Đã cập nhật biên bản.", () ->
            handoverUseCase.update(typeOf(kind), new HandoverUseCase.HandoverCommand(contractId,
                initialElectricMeter, initialWaterMeter, roomCondition, keysProvided)));
    }

    @PostMapping("/photos")
    public String addPhoto(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @RequestParam("kind") HandoverPhotoKind photoKind,
        @RequestParam("file") MultipartFile file,
        RedirectAttributes redirectAttributes
    ) {
        return handle(contractId, kind, redirectAttributes, "Đã thêm ảnh.", () -> {
            try {
                handoverUseCase.addPhoto(typeOf(kind), contractId, photoKind, file.getContentType(), file.getBytes());
            } catch (IOException ex) {
                throw new IllegalArgumentException("Không đọc được tệp ảnh", ex);
            }
        });
    }

    @PostMapping("/photos/{photoId}/delete")
    public String removePhoto(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @PathVariable UUID photoId,
        RedirectAttributes redirectAttributes
    ) {
        return handle(contractId, kind, redirectAttributes, "Đã xóa ảnh.", () ->
            handoverUseCase.removePhoto(typeOf(kind), contractId, photoId));
    }

    @GetMapping("/photos/{photoId}")
    public ResponseEntity<Void> photo(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @PathVariable UUID photoId
    ) {
        URI target = handoverUseCase.photoSignedUrl(typeOf(kind), contractId, photoId);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/items/{itemId}")
    public String updateItem(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @PathVariable UUID itemId,
        @RequestParam AssetStatus status,
        @RequestParam(required = false) String conditionNote,
        @RequestParam(required = false) BigDecimal deductionAmount,
        RedirectAttributes redirectAttributes
    ) {
        return handle(contractId, kind, redirectAttributes, "Đã cập nhật tình trạng tài sản.", () ->
            handoverUseCase.updateItem(typeOf(kind), contractId, itemId,
                new HandoverUseCase.ItemCommand(status, conditionNote, deductionAmount)));
    }

    @PostMapping("/send")
    public String send(@PathVariable UUID contractId, @PathVariable String kind, RedirectAttributes ra) {
        return handle(contractId, kind, ra, "Đã gửi biên bản cho khách thuê.", () ->
            handoverUseCase.send(typeOf(kind), contractId));
    }

    @PostMapping("/confirm")
    public String confirm(@PathVariable UUID contractId, @PathVariable String kind, RedirectAttributes ra) {
        return handle(contractId, kind, ra, "Đã xác nhận biên bản. Chờ chủ trọ duyệt.", () ->
            handoverUseCase.confirm(typeOf(kind), contractId));
    }

    @PostMapping("/approve")
    public String approve(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @RequestParam(required = false) BigDecimal finalDeduction,
        RedirectAttributes ra
    ) {
        String message = typeOf(kind) == HandoverType.CHECK_OUT
            ? "Đã duyệt check-out: hợp đồng đã kết thúc và phòng đã được trả." : "Đã duyệt biên bản check-in.";
        return handle(contractId, kind, ra, message, () ->
            handoverUseCase.approve(typeOf(kind), contractId, finalDeduction));
    }

    @PostMapping("/skip")
    public String skip(@PathVariable UUID contractId, @PathVariable String kind, RedirectAttributes ra) {
        if (!skipEnabled) {
            ra.addFlashAttribute("errorMessage", "Chức năng bỏ qua bàn giao đang tắt.");
            return "redirect:/contracts/" + contractId + "/" + kind;
        }
        String message = typeOf(kind) == HandoverType.CHECK_OUT
            ? "Đã bỏ qua check-out: hợp đồng đã kết thúc và phòng đã được trả." : "Đã bỏ qua check-in: biên bản đã duyệt.";
        return handle(contractId, kind, ra, message, () -> handoverUseCase.skip(typeOf(kind), contractId));
    }

    @PostMapping("/reject")
    public String reject(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @RequestParam String reason,
        RedirectAttributes ra
    ) {
        return handle(contractId, kind, ra, "Đã từ chối biên bản.", () ->
            handoverUseCase.reject(typeOf(kind), contractId, reason));
    }

    @PostMapping("/reopen")
    public String reopen(@PathVariable UUID contractId, @PathVariable String kind, RedirectAttributes ra) {
        return handle(contractId, kind, ra, "Đã mở lại biên bản để chỉnh sửa.", () ->
            handoverUseCase.reopen(typeOf(kind), contractId));
    }

    @PostMapping("/house-rules")
    public String saveHouseRules(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @RequestParam String content,
        RedirectAttributes ra
    ) {
        return handle(contractId, kind, ra, "Đã lưu nội quy của nhà trọ.", () -> {
            Contract contract = contractUseCase.getContract(contractId);
            handoverUseCase.saveHouseRules(contract.getPropertyId(), content);
        });
    }

    private String handle(UUID contractId, String kind, RedirectAttributes redirectAttributes, String success,
                          Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", success);
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + contractId + "/" + kind;
    }
}

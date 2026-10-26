package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.TenantDocumentUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.model.DocumentType;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Trang giấy tờ của người thuê. Xem đầy đủ trả thẳng trang (không qua redirect/flash) với Cache-Control: no-store
 * để số giấy tờ và ảnh đã giải mã không bị lưu vào session hay bộ nhớ đệm của trình duyệt.
 */
@Controller
@RequestMapping("/properties/{propertyId}/tenants/{tenantId}/documents")
public class TenantDocumentUiController {

    private static final String VIEW = "tenants/documents";

    private final TenantDocumentUseCase documentUseCase;
    private final PropertyUseCase propertyUseCase;
    private final PropertyRepositoryPort propertyRepository;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;

    public TenantDocumentUiController(
        TenantDocumentUseCase documentUseCase,
        PropertyUseCase propertyUseCase,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort
    ) {
        this.documentUseCase = documentUseCase;
        this.propertyUseCase = propertyUseCase;
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping
    public String page(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        Model model,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) {
        return render(propertyId, tenantId, model, response, redirectAttributes, null, null);
    }

    @PostMapping("/{type}/upload")
    public String upload(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @PathVariable DocumentType type,
        @RequestParam(required = false) String number,
        @RequestParam("front") MultipartFile front,
        @RequestParam(value = "back", required = false) MultipartFile back,
        RedirectAttributes redirectAttributes
    ) {
        try {
            documentUseCase.upload(new TenantDocumentUseCase.UploadDocumentCommand(
                propertyId, tenantId, type, number, toFile(front), toFile(back)));
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu giấy tờ " + type + " (đã mã hóa).");
        } catch (DomainException | IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(propertyId, tenantId);
    }

    @PostMapping("/{type}/delete")
    public String delete(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @PathVariable DocumentType type,
        RedirectAttributes redirectAttributes
    ) {
        try {
            documentUseCase.delete(propertyId, tenantId, type);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa giấy tờ " + type + ".");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(propertyId, tenantId);
    }

    @PostMapping("/{type}/reveal")
    public String reveal(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @PathVariable DocumentType type,
        @RequestParam String password,
        Model model,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) {
        RevealView revealed = null;
        String error = null;
        try {
            TenantDocumentUseCase.RevealedDocument document = documentUseCase.reveal(propertyId, tenantId, type, password);
            revealed = new RevealView(document.type().name(), document.number(), dataUri(document.front()),
                dataUri(document.back()));
        } catch (DomainException | IllegalArgumentException ex) {
            error = ex.getMessage();
        }
        return render(propertyId, tenantId, model, response, redirectAttributes, revealed, error);
    }

    private String render(
        UUID propertyId,
        UUID tenantId,
        Model model,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes,
        RevealView revealed,
        String error
    ) {
        CurrentUser user = currentUserPort.getCurrentUser();
        try {
            List<TenantDocumentUseCase.DocumentSummary> documents = documentUseCase.list(propertyId, tenantId);
            Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
            // Quyền xem giấy tờ đã được kiểm tra ở trên và chấp nhận người thuê của hợp đồng DRAFT. getProperty chỉ cho
            // người thuê đang ở, nên người thuê cần tải CCCD trước khi xác nhận check-in sẽ bị chặn nếu dùng nó.
            Property property = user != null && user.role() == UserRole.TENANT
                ? propertyRepository.findById(propertyId)
                    .orElseThrow(() -> new PropertyNotFoundException("Không tìm thấy nhà trọ: " + propertyId))
                : propertyUseCase.getProperty(propertyId);

            response.setHeader("Cache-Control", "no-store");
            model.addAttribute("currentUser", user);
            model.addAttribute("property", property);
            model.addAttribute("tenant", tenant);
            model.addAttribute("documents", documents);
            model.addAttribute("documentTypes", DocumentType.values());
            model.addAttribute("canDelete", user != null && user.role() != UserRole.TENANT);
            model.addAttribute("revealed", revealed);
            if (error != null) {
                model.addAttribute("errorMessage", error);
            }
            return VIEW;
        } catch (DomainException | java.util.NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties";
        }
    }

    private static TenantDocumentUseCase.DocumentFile toFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return new TenantDocumentUseCase.DocumentFile(file.getContentType(), file.getBytes());
    }

    private static String redirect(UUID propertyId, UUID tenantId) {
        return "redirect:/properties/" + propertyId + "/tenants/" + tenantId + "/documents";
    }

    private static String dataUri(TenantDocumentUseCase.DocumentFile file) {
        return file == null ? null
            : "data:" + file.contentType() + ";base64," + Base64.getEncoder().encodeToString(file.content());
    }

    public record RevealView(String type, String number, String frontDataUri, String backDataUri) {}
}

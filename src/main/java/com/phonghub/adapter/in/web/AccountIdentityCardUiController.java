package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.TenantDocumentUseCase;
import com.phonghub.domain.exception.DomainException;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Người thuê tự tải ảnh CCCD ở Hồ sơ cá nhân (#244), trước cả khi có hợp đồng. Giấy tờ tra theo người thuê nên
 * chủ trọ thấy ngay ở trang giấy tờ của nhà trọ khi lập hợp đồng, và biên bản check-in không đòi tải lại.
 */
@Controller
public class AccountIdentityCardUiController {

    private static final String PROFILE = "redirect:/account/profile#cccd";

    private final TenantDocumentUseCase documentUseCase;

    public AccountIdentityCardUiController(TenantDocumentUseCase documentUseCase) {
        this.documentUseCase = documentUseCase;
    }

    @PostMapping("/account/profile/identity-card")
    public String upload(
        @RequestParam(value = "front", required = false) MultipartFile front,
        @RequestParam(value = "back", required = false) MultipartFile back,
        RedirectAttributes redirectAttributes
    ) {
        try {
            documentUseCase.uploadOwnIdentityCard(TenantDocumentUiController.toFile(front),
                TenantDocumentUiController.toFile(back));
            redirectAttributes.addFlashAttribute("identityCardSuccessMessage",
                "Đã lưu ảnh CCCD (đã mã hóa). Ảnh được dùng cho hợp đồng và biên bản check-in của bạn.");
        } catch (DomainException | IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("identityCardErrorMessage", ex.getMessage());
        }
        return PROFILE;
    }
}

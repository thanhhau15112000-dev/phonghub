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
 * Người thuê tự tải GPLX ở Hồ sơ cá nhân (#252), trước cả khi có hợp đồng. Giấy tờ tra theo người thuê nên
 * chủ trọ thấy ngay ở trang giấy tờ của nhà trọ khi lập hợp đồng, và biên bản check-in dùng luôn.
 */
@Controller
public class AccountDriverLicenseUiController {

    private static final String PROFILE = "redirect:/account/profile#gplx";

    private final TenantDocumentUseCase documentUseCase;

    public AccountDriverLicenseUiController(TenantDocumentUseCase documentUseCase) {
        this.documentUseCase = documentUseCase;
    }

    @PostMapping("/account/profile/driver-license")
    public String upload(
        @RequestParam(value = "licenseNumber", required = false) String licenseNumber,
        @RequestParam(value = "front", required = false) MultipartFile front,
        @RequestParam(value = "back", required = false) MultipartFile back,
        RedirectAttributes redirectAttributes
    ) {
        try {
            documentUseCase.uploadOwnDriverLicense(
                licenseNumber,
                TenantDocumentUiController.toFile(front),
                TenantDocumentUiController.toFile(back)
            );
            redirectAttributes.addFlashAttribute("driverLicenseSuccessMessage",
                "Đã lưu giấy phép lái xe (đã mã hóa). Thông tin được dùng cho hợp đồng và biên bản check-in của bạn.");
        } catch (DomainException | IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("driverLicenseErrorMessage", ex.getMessage());
        }
        return PROFILE;
    }
}

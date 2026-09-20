package com.phonghub.adapter.in.web;

import com.phonghub.domain.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Bắt lỗi toàn cục cho các Web Controller (Thymeleaf UI).
 * Đảm bảo khi phát sinh Exception nghiệp vụ hoặc hệ thống, giao diện người dùng
 * luôn hiển thị thông báo lỗi rõ ràng thay vì im lặng hoặc văng màn hình 500.
 */
@ControllerAdvice(assignableTypes = {
    AdminUserUiController.class,
    AuthUiController.class,
    ContractUiController.class,
    DashboardUiController.class,
    MaintenanceUiController.class,
    PropertyUiController.class,
    RoomUiController.class,
    UserSwitchUiController.class
})
public class GlobalUiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalUiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public String handleDomainException(
        DomainException ex,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        log.warn("UI DomainException at URI {}: {}", request.getRequestURI(), ex.getMessage());
        redirectAttributes.addFlashAttribute(
            "errorMessage",
            ex.getMessage() != null ? ex.getMessage() : "Thao tác không thể hoàn tất theo quy tắc nghiệp vụ."
        );
        return determineRedirect(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(
        IllegalArgumentException ex,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        log.warn("UI IllegalArgumentException at URI {}: {}", request.getRequestURI(), ex.getMessage());
        redirectAttributes.addFlashAttribute(
            "errorMessage",
            ex.getMessage() != null ? ex.getMessage() : "Dữ liệu yêu cầu không hợp lệ."
        );
        return determineRedirect(request);
    }

    private String determineRedirect(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        String currentUri = request.getRequestURI();
        if (referer != null && !referer.isBlank() && !referer.endsWith(currentUri)) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }
}

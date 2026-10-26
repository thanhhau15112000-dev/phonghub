package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ManualPaymentUseCase;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.PaymentClaim;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Thanh toán ngoài cổng SePay (#205): người thuê báo đã thanh toán, người thu tiền xác nhận, từ chối hoặc ghi nhận. */
@Controller
public class PaymentClaimUiController {

    private final ManualPaymentUseCase manualPayments;

    public PaymentClaimUiController(ManualPaymentUseCase manualPayments) {
        this.manualPayments = manualPayments;
    }

    @PostMapping("/invoices/{id}/claim")
    public String submitClaim(
        @PathVariable UUID id,
        @RequestParam BigDecimal amount,
        @RequestParam PaymentClaim.Method method,
        @RequestParam(required = false) String note,
        RedirectAttributes redirectAttributes
    ) {
        try {
            manualPayments.submitClaim(id, amount, method, note);
            redirectAttributes.addFlashAttribute("successMessage",
                "Đã gửi yêu cầu xác nhận thanh toán. Chủ trọ hoặc nhân viên thu tiền sẽ kiểm tra và xác nhận.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", message(ex));
        }
        return "redirect:/invoices/" + id + "/pay";
    }

    @PostMapping("/invoices/{id}/record-payment")
    public String recordPayment(
        @PathVariable UUID id,
        @RequestParam BigDecimal amount,
        @RequestParam PaymentClaim.Method method,
        @RequestParam(required = false) String note,
        RedirectAttributes redirectAttributes
    ) {
        try {
            PaymentClaim claim = manualPayments.recordPayment(id, amount, method, note);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi nhận khoản thu và cập nhật hóa đơn.");
            return "redirect:/contracts/" + claim.contractId() + "#payments";
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", message(ex));
            return "redirect:/invoices/" + id + "/pay";
        }
    }

    @PostMapping("/payment-claims/{id}/confirm")
    public String confirm(@PathVariable UUID id, @RequestParam UUID contractId, RedirectAttributes redirectAttributes) {
        try {
            manualPayments.confirm(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận thanh toán và cập nhật hóa đơn.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", message(ex));
        }
        return "redirect:/contracts/" + contractId + "#payments";
    }

    @PostMapping("/payment-claims/{id}/reject")
    public String reject(
        @PathVariable UUID id,
        @RequestParam UUID contractId,
        @RequestParam String reason,
        RedirectAttributes redirectAttributes
    ) {
        try {
            manualPayments.reject(id, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối yêu cầu thanh toán và thông báo cho người thuê.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", message(ex));
        }
        return "redirect:/contracts/" + contractId + "#payments";
    }

    private static String message(RuntimeException ex) {
        return ex instanceof UnauthorizedPropertyAccessException
            ? "Bạn chưa được cấp quyền thao tác thanh toán này. Vui lòng liên hệ chủ trọ."
            : ex.getMessage();
    }
}

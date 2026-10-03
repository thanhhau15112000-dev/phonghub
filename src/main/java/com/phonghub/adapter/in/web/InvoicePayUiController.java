package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceType;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang hướng dẫn thanh toán một hóa đơn (tiền thuê hoặc phí sửa chữa): số tiền, mã chuyển khoản, QR. */
@Controller
@RequestMapping("/invoices")
public class InvoicePayUiController {

    private final InvoiceUseCase invoiceUseCase;
    private final MaintenanceUseCase maintenanceUseCase;
    private final CurrentUserPort currentUserPort;
    private final SepayProperties sepayProperties;
    private final SepayWebhookUseCase sepayWebhookUseCase;

    public InvoicePayUiController(
        InvoiceUseCase invoiceUseCase,
        MaintenanceUseCase maintenanceUseCase,
        CurrentUserPort currentUserPort,
        SepayProperties sepayProperties,
        SepayWebhookUseCase sepayWebhookUseCase
    ) {
        this.sepayWebhookUseCase = sepayWebhookUseCase;
        this.invoiceUseCase = invoiceUseCase;
        this.maintenanceUseCase = maintenanceUseCase;
        this.currentUserPort = currentUserPort;
        this.sepayProperties = sepayProperties;
    }

    @GetMapping("/{id}/pay")
    public String payPage(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = invoiceUseCase.getInvoice(id);

            String ticketTitle = null;
            if (invoice.getType() == InvoiceType.MAINTENANCE && invoice.getTicketId() != null) {
                try {
                    ticketTitle = maintenanceUseCase.getTicket(invoice.getTicketId()).getTitle();
                } catch (DomainException ignored) {
                    // người xem không được xem phiếu: vẫn hiển thị hóa đơn, không có tiêu đề phiếu
                }
            }

            model.addAttribute("currentUser", currentUserPort.getCurrentUser());
            model.addAttribute("invoice", invoice);
            model.addAttribute("ticketTitle", ticketTitle);
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("returnUrl", returnUrl(invoice));
            model.addAttribute("simulationEnabled", sepayProperties.isSimulationEnabled());
            model.addAttribute("bankConfigured", sepayProperties.hasBankAccount());
            model.addAttribute("bankCode", sepayProperties.getBankCode());
            model.addAttribute("bankAccountNumber", sepayProperties.getBankAccountNumber());
            model.addAttribute("bankAccountName", sepayProperties.getBankAccountName());
            model.addAttribute("paymentQrUrl", invoice.isPayable() && sepayProperties.hasBankAccount()
                ? PaymentQr.url(sepayProperties, invoice)
                : null);
            return "invoices/pay";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/contracts";
        }
    }

    /** Trạng thái thanh toán hiện tại, để trang thanh toán tự cập nhật khi webhook SePay báo tiền đã về. */
    @GetMapping("/{id}/status")
    @ResponseBody
    public Map<String, Object> status(@PathVariable UUID id) {
        Invoice invoice = invoiceUseCase.getInvoice(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", invoice.getStatus().name());
        body.put("paid", invoice.getStatus() == com.phonghub.domain.model.InvoiceStatus.PAID);
        body.put("consolidated", invoice.isConsolidated());
        body.put("paidAmount", invoice.getPaidAmount());
        body.put("remaining", invoice.remainingAmount());
        return body;
    }

    @ExceptionHandler(UnauthorizedPropertyAccessException.class)
    @ResponseBody
    public ProblemDetail unauthorizedInvoiceAccess(UnauthorizedPropertyAccessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Unauthorized Property Access");
        return problem;
    }

    /** Chế độ thử nghiệm: giả lập một lần chuyển khoản đủ số tiền (chỉ khi bật cấu hình mô phỏng). */
    @PostMapping("/{id}/simulate-payment")
    public String simulatePayment(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            invoiceUseCase.getInvoice(id); // kiểm tra quyền xem hóa đơn
            sepayWebhookUseCase.simulateTransfer(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã mô phỏng chuyển khoản (chế độ thử nghiệm).");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/invoices/" + id + "/pay";
    }

    private static String returnUrl(Invoice invoice) {
        if (invoice.getType() == InvoiceType.MAINTENANCE && invoice.getTicketId() != null) {
            return "/maintenance/" + invoice.getTicketId();
        }
        return "/contracts/" + invoice.getContractId() + "#payments";
    }
}

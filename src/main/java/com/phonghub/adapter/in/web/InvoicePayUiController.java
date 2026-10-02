package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceType;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public InvoicePayUiController(
        InvoiceUseCase invoiceUseCase,
        MaintenanceUseCase maintenanceUseCase,
        CurrentUserPort currentUserPort,
        SepayProperties sepayProperties
    ) {
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
}

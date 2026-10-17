package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.PaymentReconciliationUseCase;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationFilter;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationStatus;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.ReceiptNotAvailableException;
import com.phonghub.domain.model.PaymentReviewReason;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang giao dịch & đối soát và trang biên lai in được (#82). */
@Controller
public class PaymentReconciliationUiController {

    // Khóa theo tên enum: SpEL trong template không tra được EnumMap bằng giá trị enum.
    static final Map<String, String> STATUS_LABELS = Map.of(
        ReconciliationStatus.MATCHED.name(), "Khớp",
        ReconciliationStatus.NEEDS_REVIEW.name(), "Cần xem xét",
        ReconciliationStatus.UNMATCHED.name(), "Không khớp",
        ReconciliationStatus.IGNORED.name(), "Bỏ qua");
    static final Map<String, String> REASON_LABELS = Map.of(
        PaymentReviewReason.OVERPAID.name(), "Chuyển dư so với số còn nợ",
        PaymentReviewReason.INVOICE_NOT_PAYABLE.name(), "Hóa đơn không còn nhận tiền",
        PaymentReviewReason.UNMATCHED.name(), "Không khớp mã thanh toán");

    private final PaymentReconciliationUseCase useCase;
    private final PropertyUseCase propertyUseCase;
    private final CurrentUserPort currentUserPort;

    public PaymentReconciliationUiController(
        PaymentReconciliationUseCase useCase,
        PropertyUseCase propertyUseCase,
        CurrentUserPort currentUserPort
    ) {
        this.useCase = useCase;
        this.propertyUseCase = propertyUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping("/payments")
    public String transactions(
        @RequestParam(required = false) String propertyId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        ReconciliationFilter filter;
        try {
            filter = new ReconciliationFilter(parseUuid(propertyId), parseStatus(status), parseDate(from), parseDate(to));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            filter = new ReconciliationFilter(null, null, null, null);
            propertyId = status = from = to = null;
        }
        try {
            try {
                model.addAttribute("report", useCase.listTransactions(filter));
            } catch (DomainException ex) {
                if (filter.propertyId() == null) {
                    throw ex;
                }
                // Lọc nhà trọ không có quyền thu tiền: báo lỗi và hiển thị phạm vi được phép.
                model.addAttribute("errorMessage", ex.getMessage());
                filter = new ReconciliationFilter(null, filter.status(), filter.from(), filter.to());
                propertyId = null;
                model.addAttribute("report", useCase.listTransactions(filter));
            }
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/";
        }
        model.addAttribute("currentUser", currentUserPort.getCurrentUser());
        model.addAttribute("properties", propertyUseCase.listAccessibleProperties());
        model.addAttribute("statuses", ReconciliationStatus.values());
        model.addAttribute("statusLabels", STATUS_LABELS);
        model.addAttribute("reasonLabels", REASON_LABELS);
        model.addAttribute("filterPropertyId", propertyId);
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterFrom", from);
        model.addAttribute("filterTo", to);
        return "payments/transactions";
    }

    @GetMapping("/invoices/{id}/receipt")
    public String receipt(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("receipt", useCase.getReceipt(id));
        } catch (ReceiptNotAvailableException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/invoices/" + id + "/pay";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/contracts";
        }
        model.addAttribute("currentUser", currentUserPort.getCurrentUser());
        return "invoices/receipt";
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Nhà trọ được chọn không hợp lệ.");
        }
    }

    private static ReconciliationStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ReconciliationStatus.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Trạng thái đối soát không hợp lệ.");
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Ngày lọc không hợp lệ (định dạng yyyy-MM-dd).");
        }
    }
}

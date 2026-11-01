package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.PaymentHistoryUseCase;
import com.phonghub.application.port.in.PaymentHistoryUseCase.Filter;
import com.phonghub.application.port.in.PaymentHistoryUseCase.Method;
import com.phonghub.application.port.in.PaymentHistoryUseCase.Status;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.UserRole;
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

/** Trang lịch sử thanh toán của người thuê (#248); chủ trọ và nhân viên xem thay trong phạm vi thu tiền. */
@Controller
public class PaymentHistoryUiController {

    static final Map<String, String> STATUS_LABELS = Map.of(
        Status.SUCCESS.name(), "Thành công",
        Status.PENDING.name(), "Chờ chủ trọ xác nhận",
        Status.REJECTED.name(), "Bị từ chối",
        Status.MISMATCH.name(), "Không khớp số tiền");
    static final Map<String, String> METHOD_LABELS = Map.of(
        Method.SEPAY.name(), "Chuyển khoản (SePay)",
        Method.TENANT_CLAIM.name(), "Báo chuyển khoản",
        Method.MANAGER_RECORD.name(), "Chủ trọ ghi nhận",
        Method.DEPOSIT_OFFSET.name(), "Cấn trừ cọc");
    static final Map<String, String> TYPE_LABELS = Map.of(
        "RENT", "Tiền thuê",
        "MAINTENANCE", "Phí sửa chữa",
        "CANCELLATION", "Phí hủy hợp đồng",
        "CHECK_OUT", "Điện/nước chốt");

    private final PaymentHistoryUseCase useCase;
    private final CurrentUserPort currentUserPort;

    public PaymentHistoryUiController(PaymentHistoryUseCase useCase, CurrentUserPort currentUserPort) {
        this.useCase = useCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping("/account/payments")
    public String mine(
        @RequestParam(required = false) String contractId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(defaultValue = "1") int page,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        return render(null, contractId, status, from, to, page, model, redirectAttributes);
    }

    @GetMapping("/tenants/{tenantId}/payments")
    public String ofTenant(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) String contractId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(defaultValue = "1") int page,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        return render(tenantId, contractId, status, from, to, page, model, redirectAttributes);
    }

    private String render(UUID tenantId, String contractId, String status, String from, String to, int page,
                          Model model, RedirectAttributes redirectAttributes) {
        Filter filter;
        try {
            filter = new Filter(parseUuid(contractId), parseStatus(status), parseDate(from), parseDate(to), page, 20);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            filter = Filter.all();
            contractId = status = from = to = null;
        }
        try {
            model.addAttribute("history", useCase.history(tenantId, filter));
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/";
        }
        var user = currentUserPort.getCurrentUser();
        model.addAttribute("currentUser", user);
        model.addAttribute("viewingOwnHistory", user != null && user.role() == UserRole.TENANT);
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("basePath", tenantId == null ? "/account/payments" : "/tenants/" + tenantId + "/payments");
        model.addAttribute("statuses", Status.values());
        model.addAttribute("statusLabels", STATUS_LABELS);
        model.addAttribute("methodLabels", METHOD_LABELS);
        model.addAttribute("typeLabels", TYPE_LABELS);
        model.addAttribute("filterContractId", contractId);
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterFrom", from);
        model.addAttribute("filterTo", to);
        return "payments/history";
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Hợp đồng không hợp lệ.");
        }
    }

    private static Status parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Status.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ.");
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Ngày không hợp lệ (định dạng yyyy-MM-dd).");
        }
    }
}

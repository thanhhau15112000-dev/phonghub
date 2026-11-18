package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.InvoiceAnomalyUseCase;
import com.phonghub.application.port.in.InvoiceAnomalyUseCase.AnomalyReport;
import com.phonghub.application.port.in.InvoiceAnomalyUseCase.Severity;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang "Phân tích hóa đơn" của chủ trọ: bất thường điện/nước, hóa đơn và giao dịch theo quy tắc (#156). */
@Controller
public class InvoiceAnomalyUiController {

    // Khóa theo tên enum: SpEL trong template không tra được Map bằng giá trị enum.
    static final Map<String, String> SEVERITY_LABELS = Map.of(
        Severity.HIGH.name(), "Cao", Severity.MEDIUM.name(), "Trung bình", Severity.LOW.name(), "Thấp");

    private final InvoiceAnomalyUseCase useCase;
    private final PropertyUseCase propertyUseCase;
    private final CurrentUserPort currentUserPort;
    private final Clock clock;

    public InvoiceAnomalyUiController(
        InvoiceAnomalyUseCase useCase, PropertyUseCase propertyUseCase, CurrentUserPort currentUserPort
    ) {
        this.useCase = useCase;
        this.propertyUseCase = propertyUseCase;
        this.currentUserPort = currentUserPort;
        this.clock = Clock.system(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @GetMapping("/analysis/invoices")
    public String analysis(
        @RequestParam(required = false) String propertyId,
        @RequestParam(required = false) String month,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        YearMonth period = parseMonth(month, model);
        UUID property = parseUuid(propertyId, model);
        AnomalyReport report;
        try {
            report = useCase.analyze(property, period);
        } catch (DomainException ex) {
            // Không phải chủ trọ, hoặc nhà trọ không thuộc về mình: báo lỗi và về trang chủ như trang đối soát.
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/";
        }
        model.addAttribute("report", report);
        model.addAttribute("currentUser", currentUserPort.getCurrentUser());
        model.addAttribute("properties", propertyUseCase.listAccessibleProperties());
        model.addAttribute("filterPropertyId", property == null ? "" : property.toString());
        model.addAttribute("filterMonth", period.toString());
        model.addAttribute("severityLabels", SEVERITY_LABELS);
        model.addAttribute("spikePercent", InvoiceAnomalyUseCase.SPIKE_PERCENT);
        model.addAttribute("baselinePeriods", InvoiceAnomalyUseCase.BASELINE_PERIODS);
        return "analysis/invoices";
    }

    private YearMonth parseMonth(String value, Model model) {
        YearMonth current = YearMonth.now(clock);
        if (value == null || value.isBlank()) {
            return current;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException ex) {
            model.addAttribute("errorMessage", "Tháng không hợp lệ, hiển thị tháng hiện tại.");
            return current;
        }
    }

    private static UUID parseUuid(String value, Model model) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", "Nhà trọ được chọn không hợp lệ, hiển thị tất cả nhà trọ.");
            return null;
        }
    }
}

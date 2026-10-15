package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ExportUseCase;
import com.phonghub.application.port.in.ExportUseCase.ExportFile;
import com.phonghub.application.port.in.ExportUseCase.ExportRequest;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang chọn và tải tệp xuất dữ liệu (#85); dùng phiên đăng nhập của giao diện web. */
@Controller
public class ExportUiController {

    private final ExportUseCase exportUseCase;
    private final PropertyUseCase propertyUseCase;
    private final CurrentUserPort currentUserPort;

    public ExportUiController(ExportUseCase exportUseCase, PropertyUseCase propertyUseCase, CurrentUserPort currentUserPort) {
        this.exportUseCase = exportUseCase;
        this.propertyUseCase = propertyUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping("/exports")
    public String page(Model model) {
        model.addAttribute("currentUser", currentUserPort.getCurrentUser());
        model.addAttribute("properties", propertyUseCase.listAccessibleProperties());
        model.addAttribute("currentMonth", YearMonth.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        return "exports/index";
    }

    /** Ghi tệp thẳng vào response; lỗi (thiếu quyền, tham số sai) quay về trang xuất kèm thông báo. */
    @GetMapping("/exports/download")
    public String download(
        @RequestParam(defaultValue = "invoices") String dataset,
        @RequestParam(defaultValue = "xlsx") String format,
        @RequestParam(required = false) String propertyId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) throws IOException {
        ExportFile file;
        try {
            file = exportUseCase.export(new ExportRequest(ExportUseCase.Dataset.parse(dataset),
                ExportUseCase.Format.parse(format),
                propertyId == null || propertyId.isBlank() ? null : UUID.fromString(propertyId.trim()),
                parseMonth(from), parseMonth(to)));
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/exports";
        }
        response.setContentType(file.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.filename()).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentLength(file.content().length);
        response.getOutputStream().write(file.content());
        return null;
    }

    private static YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Tháng không hợp lệ (định dạng yyyy-MM).");
        }
    }
}

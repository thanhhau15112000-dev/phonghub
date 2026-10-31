package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ExportUseCase;
import com.phonghub.application.port.in.ExportUseCase.ExportFile;
import com.phonghub.application.port.in.ExportUseCase.Preview;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang lọc, xem trước, chọn dòng và tải tệp xuất dữ liệu (#85, #227); dùng phiên đăng nhập của giao diện web. */
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
        model.addAttribute("maxRows", ExportUseCase.MAX_ROWS);
        return "exports/index";
    }

    /** Một trang kết quả lọc dạng JSON cho bảng xem trước; lỗi trả {@code {"message": …}}. */
    @GetMapping("/exports/preview")
    @ResponseBody
    public ResponseEntity<?> preview(
        @RequestParam(defaultValue = "invoices") String dataset,
        ExportParams params,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        try {
            Preview preview = exportUseCase.preview(params.toRequest(dataset), params.toFilter(), page, size);
            return ResponseEntity.ok(preview);
        } catch (UnauthorizedPropertyAccessException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
        } catch (DomainException | IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
        }
    }

    /** Xuất mọi dòng khớp bộ lọc. Lỗi (thiếu quyền, tham số sai, không có dữ liệu) quay về trang xuất kèm thông báo. */
    @GetMapping("/exports/download")
    public String download(
        @RequestParam(defaultValue = "invoices") String dataset,
        ExportParams params,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) throws IOException {
        return send(dataset, params, null, response, redirectAttributes);
    }

    /**
     * Xuất đúng các dòng đã chọn. {@code selectAll=true} bỏ qua danh sách id và để máy chủ tự tính lại theo bộ lọc, để
     * "chọn tất cả" luôn đủ mọi trang kể cả khi trình duyệt chỉ biết một trang.
     */
    @PostMapping("/exports/download")
    public String downloadSelected(
        @RequestParam(defaultValue = "invoices") String dataset,
        ExportParams params,
        @RequestParam(required = false) Set<String> ids,
        @RequestParam(defaultValue = "false") boolean selectAll,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) throws IOException {
        return send(dataset, params, selectAll ? null : ids, response, redirectAttributes);
    }

    private String send(String dataset, ExportParams params, Set<String> ids, HttpServletResponse response,
                        RedirectAttributes redirectAttributes) throws IOException {
        ExportFile file;
        try {
            file = exportUseCase.export(params.toRequest(dataset), params.toFilter(), ids);
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
}

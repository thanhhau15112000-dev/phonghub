package com.phonghub.adapter.in.web;

import com.phonghub.config.StorageProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Lỗi vượt dung lượng multipart xảy ra trước khi chọn controller, nên advice theo gói
 * không áp dụng được; handler này không giới hạn phạm vi để luôn trả 400 thay vì 413 mặc định.
 * Form giao diện được đưa về trang trước kèm thông báo (#200) thay vì hiện JSON lỗi.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadSizeExceptionHandler {

    private final long maxFileSizeBytes;

    public UploadSizeExceptionHandler(StorageProperties storageProperties) {
        this.maxFileSizeBytes = storageProperties.getMaxFileSizeBytes();
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleUploadTooLarge(
        MaxUploadSizeExceededException ex,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String message = "Tệp vượt quá dung lượng cho phép (tối đa "
            + Math.max(1, maxFileSizeBytes / (1024 * 1024)) + " MB).";
        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        if (!request.getRequestURI().startsWith("/api/") && flash != null) {
            String target = previousPage(request);
            flash.put("errorMessage", message);
            RequestContextUtils.saveOutputFlashMap(target, request, response);
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(target)).build();
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        problem.setTitle("Invalid Argument");
        problem.setType(URI.create("https://phonghub.local/errors/invalid-argument"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    /** Đường dẫn trang chứa form (lấy từ Referer, bỏ phần máy chủ để không chuyển ra trang ngoài). */
    private static String previousPage(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                URI uri = URI.create(referer.trim());
                String path = uri.getRawPath();
                if (path != null && path.startsWith("/") && !path.startsWith("//")) {
                    return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
                }
            } catch (IllegalArgumentException ignored) {
                // Referer sai định dạng: về trang chủ
            }
        }
        return "/";
    }
}

package com.phonghub.adapter.in.web;

import java.net.URI;
import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Lỗi vượt dung lượng multipart xảy ra trước khi chọn controller, nên advice theo gói
 * không áp dụng được; handler này không giới hạn phạm vi để luôn trả 400 thay vì 413 mặc định.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadSizeExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "File exceeds the configured size limit");
        problem.setTitle("Invalid Argument");
        problem.setType(URI.create("https://phonghub.local/errors/invalid-argument"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}

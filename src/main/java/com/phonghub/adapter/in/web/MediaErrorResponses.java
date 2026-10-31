package com.phonghub.adapter.in.web;

import com.phonghub.application.service.AuthorizationService;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.FileStorageUnavailableException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Lỗi khi mở ảnh/video trên giao diện Web (#230). Ảnh thường mở bằng thẻ img hoặc tab mới, nên lỗi phải ra một trang có
 * nghĩa thay vì tab trống: chưa đăng nhập thì về trang đăng nhập, không có quyền hoặc ảnh không còn thì có thông báo.
 * Không nêu ảnh thuộc hợp đồng hay phiếu nào để khỏi lộ thông tin cho người không có quyền.
 */
final class MediaErrorResponses {

    private MediaErrorResponses() {
    }

    static ResponseEntity<byte[]> from(DomainException ex) {
        if (ex instanceof UnauthorizedPropertyAccessException) {
            if (AuthorizationService.MSG_LOGIN_REQUIRED.equals(ex.getMessage())) {
                return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/login")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
            }
            return page(HttpStatus.FORBIDDEN, "Bạn không có quyền xem tệp này.");
        }
        if (ex instanceof FileStorageUnavailableException && ex.getCause() instanceof IllegalArgumentException) {
            return page(HttpStatus.NOT_FOUND, "Tệp không còn trong kho lưu trữ, hãy tải lại tệp.");
        }
        if (ex instanceof FileStorageUnavailableException) {
            return page(HttpStatus.SERVICE_UNAVAILABLE, "Không mở được tệp từ kho lưu trữ, vui lòng thử lại sau.");
        }
        return page(HttpStatus.NOT_FOUND, "Không tìm thấy tệp này.");
    }

    /** Chỉ nhận thông báo cố định của lớp này nên không cần escape HTML. */
    private static ResponseEntity<byte[]> page(HttpStatus status, String message) {
        String html = "<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>" + status.value()
            + " — PhongHub</title></head><body style=\"font-family:sans-serif;max-width:480px;margin:15vh auto;"
            + "padding:0 16px;text-align:center\"><h1>" + status.value() + "</h1><p>" + message
            + "</p><p><a href=\"javascript:history.back()\">Quay lại</a> · <a href=\"/dashboard\">Về bảng điều khiển</a>"
            + "</p></body></html>";
        return ResponseEntity.status(status).contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .header(HttpHeaders.CACHE_CONTROL, "no-store").body(html.getBytes(StandardCharsets.UTF_8));
    }
}

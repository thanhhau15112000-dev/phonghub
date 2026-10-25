package com.phonghub.adapter.in.web;

import com.phonghub.config.StorageProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tệp vượt dung lượng (#200): form giao diện quay lại trang trước kèm thông báo, API trả 400. */
class UploadSizeExceptionHandlerTest {

    private final UploadSizeExceptionHandler handler = new UploadSizeExceptionHandler(storage(10L * 1024 * 1024));

    @Test
    @DisplayName("Form giao diện: 303 về trang có form kèm thông báo tiếng Việt")
    void uiFormGoesBackWithMessage() {
        MockHttpServletRequest request = uiRequest("https://phonghub.example/contracts/c/handover?tab=photos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ProblemDetail> result = handler.handleUploadTooLarge(tooLarge(), request, response);

        assertEquals(HttpStatus.SEE_OTHER, result.getStatusCode());
        assertEquals("/contracts/c/handover?tab=photos", result.getHeaders().getLocation().toString());
        assertEquals("Tệp vượt quá dung lượng cho phép (tối đa 10 MB).", savedFlash(request).get("errorMessage"));
    }

    @Test
    @DisplayName("Referer trỏ ra ngoài hoặc thiếu: chỉ giữ đường dẫn trong trang, không chuyển ra máy chủ khác")
    void neverRedirectsOffSite() {
        assertEquals("/phish", location(uiRequest("https://evil.example/phish")));
        assertEquals("/", location(uiRequest("https://evil.example//evil.example/x")));
        assertEquals("/", location(uiRequest(null)));
    }

    @Test
    @DisplayName("API: 400 ProblemDetail với thông báo tiếng Việt")
    void apiGetsProblemDetail() {
        MockHttpServletRequest request = uiRequest(null);
        request.setRequestURI("/api/contracts/c/handover/photos");

        ResponseEntity<ProblemDetail> result = handler.handleUploadTooLarge(tooLarge(), request,
            new MockHttpServletResponse());

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertTrue(result.getBody().getDetail().contains("tối đa 10 MB"));
    }

    private String location(MockHttpServletRequest request) {
        return handler.handleUploadTooLarge(tooLarge(), request, new MockHttpServletResponse())
            .getHeaders().getLocation().toString();
    }

    private static MockHttpServletRequest uiRequest(String referer) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/contracts/c/handover/photos");
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        if (referer != null) {
            request.addHeader("Referer", referer);
        }
        return request;
    }

    @SuppressWarnings("unchecked")
    private static FlashMap savedFlash(MockHttpServletRequest request) {
        List<FlashMap> maps = (List<FlashMap>) request.getSession()
            .getAttribute(SessionFlashMapManager.class.getName() + ".FLASH_MAPS");
        return maps.get(0);
    }

    private static MaxUploadSizeExceededException tooLarge() {
        return new MaxUploadSizeExceededException(10L * 1024 * 1024);
    }

    private static StorageProperties storage(long maxBytes) {
        StorageProperties properties = new StorageProperties();
        properties.setMaxFileSizeBytes(maxBytes);
        return properties;
    }
}

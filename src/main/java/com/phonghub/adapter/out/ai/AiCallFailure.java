package com.phonghub.adapter.out.ai;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Mô tả lỗi gọi cổng mô hình để ghi log mà không lộ nội dung yêu cầu, câu trả lời hay khóa (#322). */
final class AiCallFailure {
    private AiCallFailure() {
    }

    static String describe(RestClientException ex) {
        if (ex instanceof RestClientResponseException response) {
            return "status=" + response.getStatusCode().value() + ", error=" + ex.getClass().getSimpleName();
        }
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        return "status=none, error=" + cause.getClass().getSimpleName();
    }
}

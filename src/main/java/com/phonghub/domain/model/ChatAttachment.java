package com.phonghub.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Ảnh hoặc video gửi kèm một tin nhắn chat (#251). Tệp nằm ở kho riêng tư theo {@code objectKey}; khóa này không bao giờ
 * đưa ra trình duyệt, chỉ xem qua endpoint của phòng chat sau khi kiểm tra quyền đọc phòng.
 */
public record ChatAttachment(
    UUID id,
    int position,
    String objectKey,
    String contentType,
    String mediaType,
    long sizeBytes
) {
    public static final String IMAGE = "IMAGE";
    public static final String VIDEO = "VIDEO";

    public ChatAttachment {
        Objects.requireNonNull(id, "id must not be null");
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        Objects.requireNonNull(contentType, "contentType must not be null");
        if (!IMAGE.equals(mediaType) && !VIDEO.equals(mediaType)) {
            throw new IllegalArgumentException("mediaType must be IMAGE or VIDEO");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
    }

    public boolean isVideo() {
        return VIDEO.equals(mediaType);
    }
}

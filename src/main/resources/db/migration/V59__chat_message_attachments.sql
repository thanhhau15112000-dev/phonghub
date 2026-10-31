-- #251: ảnh/video gửi kèm tin nhắn chat, lưu ở kho riêng tư. Chỉ lưu khóa đối tượng, không lưu URL công khai;
-- xem qua endpoint của phòng chat sau khi kiểm tra quyền đọc phòng. Cột media_url/media_type (V34) giữ cho tin cũ.
CREATE TABLE chat_message_attachments (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    position SMALLINT NOT NULL CHECK (position >= 0),
    object_key VARCHAR(512) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    media_type VARCHAR(10) NOT NULL CHECK (media_type IN ('IMAGE', 'VIDEO')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_chat_message_attachments_position UNIQUE (message_id, position)
);

COMMENT ON TABLE chat_message_attachments IS 'Ảnh/video gửi kèm tin nhắn chat (#251); object_key trỏ vào kho riêng tư, không đưa ra trình duyệt';

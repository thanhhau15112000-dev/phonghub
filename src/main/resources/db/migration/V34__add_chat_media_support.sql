-- V34: Thêm hỗ trợ hình ảnh và video cho tin nhắn chat
ALTER TABLE chat_messages
    ALTER COLUMN content DROP NOT NULL,
    ADD COLUMN media_url TEXT,
    ADD COLUMN media_type VARCHAR(20);

COMMENT ON COLUMN chat_messages.media_url IS 'URL đến hình ảnh hoặc video lưu trên Supabase Storage CDN hoặc Local storage';
COMMENT ON COLUMN chat_messages.media_type IS 'Loại media: IMAGE hoặc VIDEO';

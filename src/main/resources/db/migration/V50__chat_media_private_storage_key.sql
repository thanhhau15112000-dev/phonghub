-- V50: Media chat lưu trong bucket private; cột chỉ giữ khóa đối tượng, URL có chữ ký được tạo khi đọc.
ALTER TABLE chat_messages RENAME COLUMN media_url TO media_key;

COMMENT ON COLUMN chat_messages.media_key IS 'Khóa đối tượng trong bucket private (chat/{roomId}/{uuid}.{ext})';

-- =============================================================================
-- PHONGHUB - MIGRATION V14: CHAT ROOMS AND MESSAGES
-- =============================================================================

-- 1. Bảng phòng chat (GLOBAL: Kênh chung toàn hệ thống, DIRECT: Chat riêng 1-1)
CREATE TABLE chat_rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL CHECK (type IN ('GLOBAL', 'DIRECT')),
    name VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed sẵn Kênh Chat Chung mặc định
INSERT INTO chat_rooms (id, type, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'GLOBAL', 'Kênh Chung Ban Quản Lý', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 2. Bảng thành viên phòng chat
CREATE TABLE chat_room_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMPTZ,
    last_read_at TIMESTAMPTZ,
    CONSTRAINT uk_chat_room_member UNIQUE (chat_room_id, user_id)
);

-- 3. Bảng tin nhắn
CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    sender_name VARCHAR(150) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

-- 4. Đánh chỉ mục tối ưu hiệu năng
CREATE INDEX idx_chat_messages_room_created ON chat_messages(chat_room_id, created_at DESC);
CREATE INDEX idx_chat_room_members_user ON chat_room_members(user_id) WHERE left_at IS NULL;
CREATE INDEX idx_chat_room_members_direct ON chat_room_members(user_id, chat_room_id) WHERE left_at IS NULL;

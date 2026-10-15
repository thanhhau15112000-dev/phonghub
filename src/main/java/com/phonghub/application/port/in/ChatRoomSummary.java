package com.phonghub.application.port.in;

import com.phonghub.domain.model.ChatRoomType;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.UUID;

public record ChatRoomSummary(
    UUID id,
    ChatRoomType type,
    String name,
    String lastMessageContent,
    String lastMessageSenderName,
    Instant lastMessageAt,
    int unreadCount,
    UUID peerUserId,
    String peerDisplayName,
    UserRole peerRole
) {}

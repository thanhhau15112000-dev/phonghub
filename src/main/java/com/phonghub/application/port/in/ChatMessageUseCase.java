package com.phonghub.application.port.in;

import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.model.ChatMessage;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatMessageUseCase {
    ChatMessage sendMessage(CurrentUser currentUser, UUID roomId, String content);
    ChatMessage sendMediaMessage(CurrentUser currentUser, UUID roomId, String contentType, byte[] bytes, String caption);
    URI mediaSignedUrl(CurrentUser currentUser, UUID roomId, UUID messageId);
    List<ChatMessage> getMessages(CurrentUser currentUser, UUID roomId, Instant before, int limit);
    void markAsRead(CurrentUser currentUser, UUID roomId);
    int getTotalUnreadCount(CurrentUser currentUser);
}

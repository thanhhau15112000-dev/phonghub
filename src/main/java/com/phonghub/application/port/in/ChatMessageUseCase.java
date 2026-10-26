package com.phonghub.application.port.in;

import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.model.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatMessageUseCase {
    ChatMessage sendMessage(CurrentUser currentUser, UUID roomId, String content);
    List<ChatMessage> getMessages(CurrentUser currentUser, UUID roomId, Instant before, int limit);
    void markAsRead(CurrentUser currentUser, UUID roomId);
    int getTotalUnreadCount(CurrentUser currentUser);
}

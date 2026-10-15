package com.phonghub.application.port.in;

import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.model.ChatRoom;
import java.util.List;
import java.util.UUID;

public interface ChatRoomUseCase {
    List<ChatRoomSummary> getRoomsForUser(CurrentUser currentUser);
    ChatRoom getOrCreateDirectRoom(CurrentUser currentUser, UUID targetUserId);
    ChatRoom getRoomById(CurrentUser currentUser, UUID roomId);
    List<ChatContactSummary> getAvailableContacts(CurrentUser currentUser);
    java.time.Instant getPeerLastReadAt(CurrentUser currentUser, UUID roomId);
}

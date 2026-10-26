package com.phonghub.application.service;

import com.phonghub.application.port.in.ChatContactSummary;
import com.phonghub.application.port.in.ChatMessageUseCase;
import com.phonghub.application.port.in.ChatRoomSummary;
import com.phonghub.application.port.in.ChatRoomUseCase;
import com.phonghub.application.port.out.ChatEventPublisherPort;
import com.phonghub.application.port.out.ChatMessageRepositoryPort;
import com.phonghub.application.port.out.ChatRoomRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.ChatRoomNotFoundException;
import com.phonghub.domain.exception.InvalidChatTargetException;
import com.phonghub.domain.exception.UnauthorizedChatAccessException;
import com.phonghub.domain.exception.UserNotFoundException;
import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
import com.phonghub.domain.model.ChatRoomMember;
import com.phonghub.domain.model.ChatRoomType;
import com.phonghub.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ChatService implements ChatRoomUseCase, ChatMessageUseCase {

    private final ChatRoomRepositoryPort chatRoomRepositoryPort;
    private final ChatMessageRepositoryPort chatMessageRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final ChatEventPublisherPort chatEventPublisherPort;
    private final Clock clock;

    public ChatService(
        ChatRoomRepositoryPort chatRoomRepositoryPort,
        ChatMessageRepositoryPort chatMessageRepositoryPort,
        UserRepositoryPort userRepositoryPort,
        ChatEventPublisherPort chatEventPublisherPort,
        Clock clock
    ) {
        this.chatRoomRepositoryPort = Objects.requireNonNull(chatRoomRepositoryPort, "chatRoomRepositoryPort must not be null");
        this.chatMessageRepositoryPort = Objects.requireNonNull(chatMessageRepositoryPort, "chatMessageRepositoryPort must not be null");
        this.userRepositoryPort = Objects.requireNonNull(userRepositoryPort, "userRepositoryPort must not be null");
        this.chatEventPublisherPort = Objects.requireNonNull(chatEventPublisherPort, "chatEventPublisherPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    private void assertAuthenticated(CurrentUser user) {
        if (user == null) {
            throw new UnauthorizedChatAccessException("Yêu cầu xác thực tài khoản để truy cập chat");
        }
    }

    @Override
    public List<ChatRoomSummary> getRoomsForUser(CurrentUser currentUser) {
        assertAuthenticated(currentUser);

        List<ChatRoom> userRooms = new ArrayList<>(chatRoomRepositoryPort.findRoomsByUserId(currentUser.id()));

        // Đảm bảo Kênh Chung Global luôn có trong danh sách
        boolean hasGlobal = userRooms.stream().anyMatch(ChatRoom::isGlobal);
        if (!hasGlobal) {
            Optional<ChatRoom> globalRoomOpt = chatRoomRepositoryPort.findById(ChatRoom.GLOBAL_ROOM_ID);
            ChatRoom globalRoom = globalRoomOpt.orElseGet(() -> chatRoomRepositoryPort.save(ChatRoom.createGlobal()));
            userRooms.add(0, globalRoom);
        }

        List<ChatRoomSummary> summaries = new ArrayList<>();
        for (ChatRoom room : userRooms) {
            Optional<ChatMessage> latestMsgOpt = chatMessageRepositoryPort.findLatestMessageByRoomId(room.id());
            Instant lastRead = chatRoomRepositoryPort.getLastRead(room.id(), currentUser.id());
            int unreadCount = chatMessageRepositoryPort.countUnreadMessages(room.id(), currentUser.id(), lastRead);

            String lastMsgText = latestMsgOpt.map(m -> {
                if (m.hasMedia()) {
                    String mediaLabel = m.isImage() ? "[Hình ảnh]" : "[Video]";
                    return (m.content() != null && !m.content().isBlank()) ? mediaLabel + " " + m.content() : mediaLabel;
                }
                return m.content() != null ? m.content() : "";
            }).orElse("");
            String lastMsgSender = latestMsgOpt.map(ChatMessage::senderName).orElse("");
            Instant lastMsgTime = latestMsgOpt.map(ChatMessage::createdAt).orElse(room.createdAt());

            UUID peerId = null;
            String peerName = null;
            var peerRole = (com.phonghub.domain.model.UserRole) null;

            if (room.isDirect()) {
                Optional<ChatRoomMember> peerOpt = room.members().stream()
                    .filter(m -> !m.userId().equals(currentUser.id()))
                    .findFirst();
                if (peerOpt.isPresent()) {
                    peerId = peerOpt.get().userId();
                    peerName = peerOpt.get().displayName();
                    peerRole = peerOpt.get().role();
                }
            }

            String displayName = room.isGlobal() ? "Kênh Chung Ban Quản Lý" : (peerName != null ? peerName : room.name());

            summaries.add(new ChatRoomSummary(
                room.id(),
                room.type(),
                displayName,
                lastMsgText,
                lastMsgSender,
                lastMsgTime,
                unreadCount,
                peerId,
                peerName,
                peerRole
            ));
        }

        // Sắp xếp: Kênh Global luôn ở vị trí đầu tiên, các phòng khác sắp xếp theo tin nhắn mới nhất
        summaries.sort((a, b) -> {
            if (a.type() == ChatRoomType.GLOBAL) return -1;
            if (b.type() == ChatRoomType.GLOBAL) return 1;
            return b.lastMessageAt().compareTo(a.lastMessageAt());
        });

        return summaries;
    }

    @Override
    public ChatRoom getOrCreateDirectRoom(CurrentUser currentUser, UUID targetUserId) {
        assertAuthenticated(currentUser);
        if (targetUserId == null) {
            throw new IllegalArgumentException("Target user ID cannot be null");
        }
        if (currentUser.id().equals(targetUserId)) {
            throw new InvalidChatTargetException("Không thể tạo cuộc trò chuyện riêng với chính mình");
        }

        User targetUser = userRepositoryPort.findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException("Không tìm thấy người dùng với ID: " + targetUserId));

        Optional<ChatRoom> existingRoom = chatRoomRepositoryPort.findDirectRoomBetween(currentUser.id(), targetUserId);
        if (existingRoom.isPresent()) {
            return existingRoom.get();
        }

        ChatRoomMember member1 = ChatRoomMember.create(
            UUID.randomUUID(), currentUser.id(), currentUser.fullName(), currentUser.role()
        );
        ChatRoomMember member2 = ChatRoomMember.create(
            UUID.randomUUID(), targetUser.id(), targetUser.fullName(), targetUser.role()
        );

        ChatRoom newRoom = ChatRoom.createDirect(targetUser.fullName(), List.of(member1, member2));
        return chatRoomRepositoryPort.save(newRoom);
    }

    @Override
    public ChatRoom getRoomById(CurrentUser currentUser, UUID roomId) {
        assertAuthenticated(currentUser);
        ChatRoom room = chatRoomRepositoryPort.findById(roomId)
            .orElseThrow(() -> new ChatRoomNotFoundException("Không tìm thấy phòng chat: " + roomId));

        if (room.isDirect() && !room.hasMember(currentUser.id())) {
            throw new UnauthorizedChatAccessException("Bạn không có quyền truy cập vào phòng chat riêng này");
        }

        return room;
    }

    @Override
    public List<ChatContactSummary> getAvailableContacts(CurrentUser currentUser) {
        assertAuthenticated(currentUser);
        return userRepositoryPort.findAll().stream()
            .filter(u -> !u.id().equals(currentUser.id()))
            .sorted(Comparator.comparing(User::fullName))
            .map(u -> new ChatContactSummary(u.id(), u.fullName(), u.email(), u.role()))
            .toList();
    }

    @Override
    public ChatMessage sendMessage(CurrentUser currentUser, UUID roomId, String content) {
        assertAuthenticated(currentUser);
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống");
        }

        // Kiểm tra quyền truy cập phòng trước khi gửi tin
        ChatRoom room = getRoomById(currentUser, roomId);

        ChatMessage message = ChatMessage.create(room.id(), currentUser.id(), currentUser.fullName(), content.trim(),
            clock.instant());
        ChatMessage saved = chatMessageRepositoryPort.save(message);

        // Đánh dấu người gửi đã đọc tại thời điểm gửi
        chatRoomRepositoryPort.updateLastRead(roomId, currentUser.id(), clock.instant());

        // Phát sự kiện real-time tới những người được đọc phòng
        chatEventPublisherPort.publishNewMessage(room, saved);

        return saved;
    }

    @Override
    public List<ChatMessage> getMessages(CurrentUser currentUser, UUID roomId, Instant before, int limit) {
        assertAuthenticated(currentUser);
        // Kiểm tra quyền truy cập phòng
        ChatRoom room = getRoomById(currentUser, roomId);

        int queryLimit = (limit <= 0 || limit > 100) ? 30 : limit;
        List<ChatMessage> messages = chatMessageRepositoryPort.findByRoomId(roomId, before, queryLimit);

        // Khi người dùng tải tin nhắn của phòng, tự động đánh dấu đã đọc
        markRoomAsRead(currentUser, room);

        return messages;
    }

    @Override
    public void markAsRead(CurrentUser currentUser, UUID roomId) {
        if (currentUser == null || roomId == null) {
            return;
        }
        markRoomAsRead(currentUser, getRoomById(currentUser, roomId));
    }

    private void markRoomAsRead(CurrentUser currentUser, ChatRoom room) {
        Instant now = clock.instant();
        chatRoomRepositoryPort.updateLastRead(room.id(), currentUser.id(), now);
        // "Đã xem" chỉ hiển thị trong phòng 1-1; Kênh Chung không cần báo cho cả hệ thống mỗi lần có người mở
        if (room.isDirect()) {
            chatEventPublisherPort.publishReadReceipt(room, currentUser.id(), now);
        }
    }

    @Override
    public Instant getPeerLastReadAt(CurrentUser currentUser, UUID roomId) {
        assertAuthenticated(currentUser);
        ChatRoom room = getRoomById(currentUser, roomId);
        if (!room.isDirect()) {
            return null;
        }
        return room.getPeerMember(currentUser.id())
            .map(ChatRoomMember::lastReadAt)
            .orElse(null);
    }

    @Override
    public int getTotalUnreadCount(CurrentUser currentUser) {
        if (currentUser == null) {
            return 0;
        }
        List<ChatRoomSummary> rooms = getRoomsForUser(currentUser);
        return rooms.stream().mapToInt(ChatRoomSummary::unreadCount).sum();
    }
}

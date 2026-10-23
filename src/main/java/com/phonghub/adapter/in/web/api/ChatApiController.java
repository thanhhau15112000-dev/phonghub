package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.out.event.SseChatEventPublisher;
import com.phonghub.application.port.in.ChatContactSummary;
import com.phonghub.application.port.in.ChatMessageUseCase;
import com.phonghub.application.port.in.ChatRoomSummary;
import com.phonghub.application.port.in.ChatRoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatApiController {

    private final ChatRoomUseCase chatRoomUseCase;
    private final ChatMessageUseCase chatMessageUseCase;
    private final CurrentUserPort currentUserPort;
    private final SseChatEventPublisher sseChatEventPublisher;

    public ChatApiController(
        ChatRoomUseCase chatRoomUseCase,
        ChatMessageUseCase chatMessageUseCase,
        CurrentUserPort currentUserPort,
        SseChatEventPublisher sseChatEventPublisher
    ) {
        this.chatRoomUseCase = chatRoomUseCase;
        this.chatMessageUseCase = chatMessageUseCase;
        this.currentUserPort = currentUserPort;
        this.sseChatEventPublisher = sseChatEventPublisher;
    }

    private CurrentUser requireUser() {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            throw new InvalidCredentialsException("Yêu cầu xác thực tài khoản để truy cập chat");
        }
        return user;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        CurrentUser user = requireUser();
        return sseChatEventPublisher.subscribe(user.id());
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomSummary>> getRooms() {
        CurrentUser user = requireUser();
        return ResponseEntity.ok(chatRoomUseCase.getRoomsForUser(user));
    }

    public record CreateDirectRoomRequest(@NotNull UUID targetUserId) {}

    @PostMapping("/rooms/direct")
    public ResponseEntity<ChatRoom> createOrGetDirectRoom(@Valid @RequestBody CreateDirectRoomRequest request) {
        CurrentUser user = requireUser();
        ChatRoom room = chatRoomUseCase.getOrCreateDirectRoom(user, request.targetUserId());
        return ResponseEntity.ok(room);
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
        @PathVariable UUID roomId,
        @RequestParam(required = false) Instant before,
        @RequestParam(defaultValue = "30") int limit
    ) {
        CurrentUser user = requireUser();
        List<ChatMessage> messages = chatMessageUseCase.getMessages(user, roomId, before, limit);
        Instant peerLastRead = chatRoomUseCase.getPeerLastReadAt(user, roomId);
        var responseBuilder = ResponseEntity.ok();
        if (peerLastRead != null) {
            responseBuilder.header("X-Peer-Last-Read-At", peerLastRead.toString());
        }
        return responseBuilder.body(messages.stream().map(ChatMessageResponse::from).toList());
    }

    @GetMapping("/rooms/{roomId}/seen")
    public ResponseEntity<Map<String, String>> getSeenStatus(@PathVariable UUID roomId) {
        CurrentUser user = requireUser();
        Instant peerLastRead = chatRoomUseCase.getPeerLastReadAt(user, roomId);
        return ResponseEntity.ok(Map.of(
            "peerLastReadAt", peerLastRead != null ? peerLastRead.toString() : ""
        ));
    }

    public record SendMessageRequest(@NotBlank String content) {}

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
        @PathVariable UUID roomId,
        @Valid @RequestBody SendMessageRequest request
    ) {
        CurrentUser user = requireUser();
        ChatMessage message = chatMessageUseCase.sendMessage(user, roomId, request.content());
        return ResponseEntity.ok(ChatMessageResponse.from(message));
    }

    @PostMapping(value = "/rooms/{roomId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatMessageResponse> sendMedia(
        @PathVariable UUID roomId,
        @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
        @RequestParam(value = "caption", required = false) String caption
    ) throws java.io.IOException {
        CurrentUser user = requireUser();
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ChatMessage message = chatMessageUseCase.sendMediaMessage(
            user,
            roomId,
            file.getContentType(),
            file.getBytes(),
            caption
        );
        return ResponseEntity.ok(ChatMessageResponse.from(message));
    }

    @GetMapping("/rooms/{roomId}/messages/{messageId}/media")
    public ResponseEntity<Void> media(@PathVariable UUID roomId, @PathVariable UUID messageId) {
        CurrentUser user = requireUser();
        URI target = chatMessageUseCase.mediaSignedUrl(user, roomId, messageId);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID roomId) {
        CurrentUser user = requireUser();
        chatMessageUseCase.markAsRead(user, roomId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/contacts")
    public ResponseEntity<List<ChatContactSummary>> getContacts() {
        CurrentUser user = requireUser();
        return ResponseEntity.ok(chatRoomUseCase.getAvailableContacts(user));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount() {
        CurrentUser user = currentUserPort.getCurrentUser();
        int count = user == null ? 0 : chatMessageUseCase.getTotalUnreadCount(user);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }
}

package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.ChatContactSummary;
import com.phonghub.application.port.in.ChatMessageUseCase;
import com.phonghub.application.port.in.ChatMessageUseCase.MediaUpload;
import com.phonghub.application.port.in.ChatRoomSummary;
import com.phonghub.application.port.in.ChatRoomUseCase;
import com.phonghub.application.port.out.ChatSubscriptionPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.ChatMessageNotFoundException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatApiController {

    private final ChatRoomUseCase chatRoomUseCase;
    private final ChatMessageUseCase chatMessageUseCase;
    private final CurrentUserPort currentUserPort;
    private final ChatSubscriptionPort chatSubscriptionPort;
    /** Tin cũ trước #201 chỉ được chuyển tới bucket công khai cũ trên Supabase của chính hệ thống. */
    private final String legacyMediaPrefix;

    public ChatApiController(
        ChatRoomUseCase chatRoomUseCase,
        ChatMessageUseCase chatMessageUseCase,
        CurrentUserPort currentUserPort,
        ChatSubscriptionPort chatSubscriptionPort,
        @Value("${supabase.url:}") String supabaseUrl
    ) {
        this.chatRoomUseCase = chatRoomUseCase;
        this.chatMessageUseCase = chatMessageUseCase;
        this.currentUserPort = currentUserPort;
        this.chatSubscriptionPort = chatSubscriptionPort;
        this.legacyMediaPrefix = supabaseUrl == null || supabaseUrl.isBlank()
            ? null
            : supabaseUrl.trim().replaceAll("/+$", "") + "/storage/v1/object/public/";
    }

    private CurrentUser requireUser() {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            throw new InvalidCredentialsException("Yêu cầu xác thực tài khoản để truy cập chat");
        }
        return user;
    }

    /** Luồng SSE của widget chat; proxy không được nén hay gom dữ liệu, nếu không tin sẽ đến trễ. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        CurrentUser user = requireUser();
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
            .header("X-Accel-Buffering", "no")
            .body(chatSubscriptionPort.subscribe(user.id()));
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

    /** Gửi ảnh/video kèm chữ (#251): trường {@code files} lặp lại tối đa 5 lần, {@code content} tùy chọn. */
    @PostMapping(value = "/rooms/{roomId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatMessageResponse> sendAttachments(
        @PathVariable UUID roomId,
        @RequestParam(value = "files", required = false) List<MultipartFile> files,
        @RequestParam(value = "content", required = false) String content
    ) throws IOException {
        CurrentUser user = requireUser();
        List<MediaUpload> uploads = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    uploads.add(new MediaUpload(file.getContentType(), file.getBytes()));
                }
            }
        }
        ChatMessage message = chatMessageUseCase.sendMediaMessage(user, roomId, content, uploads);
        return ResponseEntity.ok(ChatMessageResponse.from(message));
    }

    /**
     * Xem ảnh/video đính kèm sau khi kiểm tra quyền đọc phòng. Kho cho URL trình duyệt mở được thì chuyển hướng sang URL
     * ký ngắn hạn; kho in-memory (profile docker/test) thì trả tệp qua máy chủ, có hỗ trợ {@code Range} để tua video.
     */
    @GetMapping("/rooms/{roomId}/messages/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> attachment(
        @PathVariable UUID roomId,
        @PathVariable UUID messageId,
        @PathVariable UUID attachmentId
    ) {
        CurrentUser user = requireUser();
        URI target = chatMessageUseCase.attachmentSignedUrl(user, roomId, messageId, attachmentId);
        if ("https".equalsIgnoreCase(target.getScheme()) || "http".equalsIgnoreCase(target.getScheme())) {
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        ChatMessageUseCase.AttachmentFile file = chatMessageUseCase.attachmentFile(user, roomId, messageId, attachmentId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .header("X-Content-Type-Options", "nosniff")
            .body(new ByteArrayResource(file.content()));
    }

    /**
     * Tệp ở tin cũ trước #201: kiểm tra quyền đọc phòng như tệp mới, rồi chuyển tới URL đã lưu nếu nó nằm ở bucket công
     * khai cũ trên Supabase. Tệp cục bộ cũ ({@code /uploads/chat-media/…}, đã gỡ cùng #201) thì báo không tìm thấy.
     */
    @GetMapping("/rooms/{roomId}/messages/{messageId}/legacy-media")
    public ResponseEntity<Void> legacyMedia(@PathVariable UUID roomId, @PathVariable UUID messageId) {
        CurrentUser user = requireUser();
        String stored = chatMessageUseCase.legacyMediaUrl(user, roomId, messageId);
        if (legacyMediaPrefix == null || !stored.startsWith(legacyMediaPrefix)) {
            throw new ChatMessageNotFoundException("Tệp đính kèm cũ không còn trên kho lưu trữ.");
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, stored)
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

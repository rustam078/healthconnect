package in.healthconnect.community.controller;

import in.healthconnect.community.dto.ChatHistoryDto;
import in.healthconnect.community.dto.ChatMessageDto;
import in.healthconnect.community.dto.SendMessageRequest;
import in.healthconnect.community.realtime.ChatStreamRegistry;
import in.healthconnect.community.service.ChatMessageService;
import in.healthconnect.portal.CurrentUserProvider;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// The live stream and message endpoints. The stream carries every community the user belongs to.
@RestController
@RequestMapping("/api/v1/communities")
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatStreamRegistry streamRegistry;
    private final CurrentUserProvider currentUser;

    public ChatController(ChatMessageService chatMessageService, ChatStreamRegistry streamRegistry,
                          CurrentUserProvider currentUser) {
        this.chatMessageService = chatMessageService;
        this.streamRegistry = streamRegistry;
        this.currentUser = currentUser;
    }

    // One Server-Sent-Events stream per screen: connected, message, message_deleted, communities_changed.
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return streamRegistry.open(currentUser.currentUser().getId());
    }

    @GetMapping("/{communityId}/messages")
    public ResponseEntity<ApiResponse<ChatHistoryDto>> history(@PathVariable Integer communityId,
                                                               @RequestParam(required = false) Integer beforeId,
                                                               @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.history(currentUser.currentUser(), communityId, beforeId, limit)));
    }

    @PostMapping("/{communityId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageDto>> send(@PathVariable Integer communityId,
                                                            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.send(currentUser.currentUser(), communityId, request), "Sent"));
    }

    @DeleteMapping("/{communityId}/messages/{messageId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer communityId,
                                                    @PathVariable Integer messageId) {
        chatMessageService.delete(currentUser.currentUser(), communityId, messageId);
        return ResponseEntity.ok(ApiResponse.success(null, "Deleted"));
    }
}

package in.healthconnect.community.dto;

import in.healthconnect.community.entity.ChatMessage;
import in.healthconnect.community.entity.enums.MessageType;

import java.time.Instant;

// A chat line for the client. attachmentId, when set, is fetched from GET /api/v1/files/{id};
// attachmentName is the original file name (shown on a file card for non-image attachments).
public record ChatMessageDto(Integer id, Integer communityId, Integer senderId, String senderName,
                             MessageType type, String body, Integer attachmentId, String attachmentName,
                             Instant createdAt) {

    public static ChatMessageDto from(ChatMessage message, String senderName) {
        return from(message, senderName, null);
    }

    public static ChatMessageDto from(ChatMessage message, String senderName, String attachmentName) {
        return new ChatMessageDto(message.getId(), message.getCommunityId(), message.getSenderId(), senderName,
                message.getType(), message.getBody(), message.getAttachmentId(), attachmentName, message.getCreatedAt());
    }
}

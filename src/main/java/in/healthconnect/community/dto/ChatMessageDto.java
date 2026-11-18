package in.healthconnect.community.dto;

import in.healthconnect.community.entity.ChatMessage;
import in.healthconnect.community.entity.enums.MessageType;

import java.time.Instant;

// A chat line for the client. senderAvatarFileId (when set) drives the sender's avatar image;
// attachmentId is fetched from GET /api/v1/files/{id}; attachmentName shows on a file card.
public record ChatMessageDto(Integer id, Integer communityId, Integer senderId, String senderName,
                             Integer senderAvatarFileId, MessageType type, String body, Integer attachmentId,
                             String attachmentName, Instant createdAt) {

    public static ChatMessageDto from(ChatMessage message, String senderName) {
        return from(message, senderName, null, null);
    }

    public static ChatMessageDto from(ChatMessage message, String senderName, Integer senderAvatarFileId,
                                      String attachmentName) {
        return new ChatMessageDto(message.getId(), message.getCommunityId(), message.getSenderId(), senderName,
                senderAvatarFileId, message.getType(), message.getBody(), message.getAttachmentId(), attachmentName,
                message.getCreatedAt());
    }
}

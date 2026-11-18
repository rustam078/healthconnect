package in.healthconnect.community.entity;

import in.healthconnect.community.entity.enums.MessageType;
import in.healthconnect.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One chat line in a community. Soft-deleted (BaseEntity) so a removed message simply drops out
// of queries; an optional attachment_id points at a StoredFile for image/file messages.
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage extends BaseEntity {

    @Column(name = "community_id", nullable = false)
    private Integer communityId;

    @Column(name = "sender_id", nullable = false)
    private Integer senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type;

    @Column(length = 4000)
    private String body;

    @Column(name = "attachment_id")
    private Integer attachmentId;

    public ChatMessage(Integer communityId, Integer senderId, MessageType type) {
        this.communityId = communityId;
        this.senderId = senderId;
        this.type = type;
    }

    public void markDeleted() {
        setDeleted(true);
    }
}

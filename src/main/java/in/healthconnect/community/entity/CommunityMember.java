package in.healthconnect.community.entity;

import in.healthconnect.community.entity.enums.MemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Who is in a community, their role, and how far they have read (last_read_message_id). A member
// row is hard-deleted on leave, so it is a plain entity (no soft-delete base).
@Entity
@Table(name = "community_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"community_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CommunityMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "community_id", nullable = false)
    private Integer communityId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(name = "last_read_message_id")
    private Integer lastReadMessageId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public CommunityMember(Integer communityId, Integer userId, MemberRole role) {
        this.communityId = communityId;
        this.userId = userId;
        this.role = role;
    }
}

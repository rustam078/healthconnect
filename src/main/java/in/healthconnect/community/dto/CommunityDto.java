package in.healthconnect.community.dto;

import in.healthconnect.community.entity.enums.CommunityVisibility;
import in.healthconnect.community.entity.enums.MemberRole;

// A community as the caller sees it: with their role + unread count when they are a member,
// or just the member count when it is a discover row (not joined yet).
public record CommunityDto(Integer id, String name, String description, CommunityVisibility visibility,
                           MemberRole role, long memberCount, long unreadCount) {

    // Discover row: not a member, so no role and nothing unread.
    public CommunityDto(Integer id, String name, String description, CommunityVisibility visibility, long memberCount) {
        this(id, name, description, visibility, null, memberCount, 0L);
    }
}

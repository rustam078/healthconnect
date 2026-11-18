package in.healthconnect.community.dto;

import in.healthconnect.community.entity.enums.MemberRole;

public record MemberDto(Integer userId, String username, String displayName, MemberRole role) {
}

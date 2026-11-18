package in.healthconnect.community.dto;

import in.healthconnect.community.entity.enums.CommunityVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCommunityRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull CommunityVisibility visibility,
        List<Integer> memberIds) {
}

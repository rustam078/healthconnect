package in.healthconnect.setting.dto.response;

import in.healthconnect.setting.entity.AppSetting;
import lombok.*;

import java.time.Instant;

// One setting, sent back to the client.
//
// IMPORTANT: when the setting is marked secret, `value` is MASKED here. This is the only
// place masking happens, so it cannot be forgotten by a caller.
// Server-side code that needs the real value uses SettingService.getRequired(name).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSettingResponse {

    private Integer id;
    private String name;
    private String value;   // masked when secret is true
    private Boolean secret;
    private String description;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public static AppSettingResponse from(AppSetting setting) {
        boolean secret = Boolean.TRUE.equals(setting.getSecret());
        return AppSettingResponse.builder()
                .id(setting.getId())
                .name(setting.getName())
                .value(secret ? mask(setting.getValue()) : setting.getValue())
                .secret(secret)
                .description(setting.getDescription())
                .enabled(setting.getEnabled())
                .createdAt(setting.getCreatedAt())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }

    // Show just enough to recognise which key is stored, and no more:
    //   nothing to show      -> null
    //   8 characters or less -> "****"   (4 + 4 would reveal the whole value)
    //   longer               -> first 4 + "****" + last 4
    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}

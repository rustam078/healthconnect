package in.healthconnect.setting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// What the client sends to create or update a setting.
//
// Update reuses this DTO, so a client must still send `name` to pass validation - but the
// service IGNORES it and never renames a stored setting. Other code looks settings up by
// name, so a rename would silently break them (same rule as Widget.code).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppSettingRequest {

    @NotBlank
    @Size(max = 150)
    private String name;

    @NotBlank
    private String value;

    // optional; defaults to false when creating
    private Boolean secret;

    @Size(max = 500)
    private String description;

    // optional; defaults to true when creating
    private Boolean enabled;
}

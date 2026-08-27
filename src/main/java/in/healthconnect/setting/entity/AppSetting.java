package in.healthconnect.setting.entity;

import in.healthconnect.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

// One row = one application setting, e.g. name="nim.api-key".
// Deliberately generic: any part of the app can store a value here instead of
// putting it in application.properties (which is tracked in git and needs a restart).
//
// Extends BaseEntity, so it gets id, created/updated time, and the soft-delete flag.
@Entity
@Table(
        name = "app_setting",
        uniqueConstraints = @UniqueConstraint(name = "uk_app_setting_name", columnNames = "name")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE app_setting SET is_deleted = true WHERE id = ?")
public class AppSetting extends BaseEntity {

    // The lookup key, e.g. "nim.api-key". Unique, and never changed after creation.
    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    // The value. Column is "setting_value" because VALUE is a MySQL keyword.
    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;

    // When true, the value is MASKED whenever it is sent to a client.
    @Column(name = "secret", nullable = false)
    @Builder.Default
    private Boolean secret = false;

    // Free text so whoever reads the settings list knows what this is for.
    @Column(name = "description", length = 500)
    private String description;

    // Turn a setting off without deleting it. A disabled setting reads as absent.
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}

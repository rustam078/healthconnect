package in.healthconnect.security.entity;

import in.healthconnect.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

import java.util.LinkedHashSet;
import java.util.Set;

// A role groups a set of permissions. A user has one role.
// "system" roles (like ADMIN) are seeded and cannot be deleted.
@Entity
@Table(name = "role", uniqueConstraints = @UniqueConstraint(name = "uk_role_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE role SET is_deleted = true WHERE id = ?")
public class Role extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean system = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<RolePermission> permissions = new LinkedHashSet<>();
}

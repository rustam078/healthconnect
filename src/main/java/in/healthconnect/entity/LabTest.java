package in.healthconnect.entity;

import in.healthconnect.entity.enums.LabTestCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

// A test the hospital offers (the catalogue the doctor searches when ordering tests).
@Entity
@Table(name = "lab_test", uniqueConstraints = @UniqueConstraint(name = "uk_lab_test_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE lab_test SET is_deleted = true WHERE id = ?")
public class LabTest extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private LabTestCategory category;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}

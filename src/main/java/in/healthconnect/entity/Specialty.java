package in.healthconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Entity
@Table(
name = "specialties",
uniqueConstraints = {
                @UniqueConstraint(
                  name = "uk_specialty_name",columnNames = "name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE specialties SET is_deleted = true WHERE id = ?")
public class Specialty extends BaseEntity {
    @Column(name = "name", nullable = false, unique = true, length = 100)
private String name;
    @Column(name = "description", length = 500)
private String description;
}
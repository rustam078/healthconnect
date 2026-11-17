package in.healthconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

// A test/investigation the doctor advised at a visit (e.g. "CBC", "fasting required").
@Entity
@Table(name = "recommended_test")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE recommended_test SET is_deleted = true WHERE id = ?")
public class RecommendedTest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_test_consultation"))
    private Consultation consultation;

    @Column(name = "test_name", nullable = false, length = 150)
    private String testName;

    @Column(name = "instructions", length = 255)
    private String instructions;
}

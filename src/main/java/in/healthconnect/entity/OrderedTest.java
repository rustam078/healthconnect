package in.healthconnect.entity;

import in.healthconnect.entity.enums.LabTestCategory;
import in.healthconnect.entity.enums.OrderedTestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

// A test ordered for one appointment. Name/category are snapshotted so later catalogue edits
// don't rewrite history; report_id links the uploaded result once it's available.
@Entity
@Table(name = "ordered_test")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE ordered_test SET is_deleted = true WHERE id = ?")
public class OrderedTest extends BaseEntity {

    @Column(name = "appointment_id", nullable = false)
    private Integer appointmentId;

    @Column(name = "test_name", nullable = false, length = 150)
    private String testName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private LabTestCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OrderedTestStatus status = OrderedTestStatus.ORDERED;

    @Column(name = "report_id")
    private Integer reportId;
}

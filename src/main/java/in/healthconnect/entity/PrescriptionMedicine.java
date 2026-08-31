package in.healthconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Entity
@Table(name = "prescription_medicines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE prescription_medicines SET is_deleted = true WHERE id = ?")
public class PrescriptionMedicine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_medicine_consultation"))
    private Consultation consultation;

    @Column(name = "medicine_name", nullable = false, length = 150)
    private String medicineName;

    @Column(name = "dosage", length = 50)
    private String dosage;        // e.g. "500mg"

    @Column(name = "frequency", length = 50)
    private String frequency;     // e.g. "1-0-1"

    @Column(name = "duration", length = 50)
    private String duration;      // e.g. "5 days"

    @Column(name = "instructions", length = 255)
    private String instructions;  // e.g. "after food"
}

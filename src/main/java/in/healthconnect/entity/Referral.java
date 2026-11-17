package in.healthconnect.entity;

import in.healthconnect.entity.enums.ReferralStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

// A doctor referring a patient (on a visit) to a specialty, with a reason.
@Entity
@Table(name = "referral")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE referral SET is_deleted = true WHERE id = ?")
public class Referral extends BaseEntity {

    @Column(name = "appointment_id", nullable = false)
    private Integer appointmentId;

    @Column(name = "specialty_id", nullable = false)
    private Integer specialtyId;

    @Column(name = "from_doctor_id", nullable = false)
    private Integer fromDoctorId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReferralStatus status = ReferralStatus.REQUESTED;
}

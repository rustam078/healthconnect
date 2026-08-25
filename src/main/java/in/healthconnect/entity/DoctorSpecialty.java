package in.healthconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Table(name = "doctor_specialties",
        uniqueConstraints = {@UniqueConstraint(name = "uk_doctor_specialty",
                columnNames = {"doctor_id", "specialty_id"})})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE doctor_specialties SET is_deleted = true WHERE id = ?")
public class DoctorSpecialty extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id",
            nullable = false, foreignKey = @ForeignKey(name = "fk_doctor_specialty_doctor"))
    private Doctor doctor;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialty_id",
            nullable = false, foreignKey = @ForeignKey(name = "fk_doctor_specialty_specialty"))
    private Specialty specialty;
}
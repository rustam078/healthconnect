package in.healthconnect.entity;

import jakarta.persistence.*;
import lombok.*;

@Table(name = "doctor_specialties_map",
        uniqueConstraints = {@UniqueConstraint(name = "uk_doctor_specialty",
                columnNames = {"doctor_id", "specialty_id"})})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorSpecialtyMap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id",
            nullable = false, foreignKey = @ForeignKey(name = "fk_doctor_specialty_doctor"))
    private Doctor doctor;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialty_id",
            nullable = false, foreignKey = @ForeignKey(name = "fk_doctor_specialty_specialty"))
    private Specialty specialty;
}
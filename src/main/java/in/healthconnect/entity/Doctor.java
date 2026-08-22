package in.healthconnect.entity;

import in.healthconnect.entity.enums.Gender;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
name = "doctors",
uniqueConstraints = {
                @UniqueConstraint(
                  name = "uk_doctor_code",columnNames = "doctor_code"),
                @UniqueConstraint(name = "uk_doctor_email",columnNames = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE doctors SET is_deleted = true WHERE id = ?")
public class Doctor extends BaseEntity {

    @Column(name = "doctor_code", nullable = false, unique = true, length = 20)
private String doctorCode;

    @Column(name = "first_name", nullable = false, length = 50)
private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 100)
private String email;
  
    @Column(name = "phone", nullable = false, length = 15)
private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
private Gender gender;

    @Column(name = "date_of_birth", nullable = false)
private LocalDate dateOfBirth;

    @Column(name = "qualification", nullable = false, length = 200)
private String qualification;

    @Column(name = "experience_years", nullable = false)
private Integer experienceYears;

    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
private BigDecimal consultationFee;

}
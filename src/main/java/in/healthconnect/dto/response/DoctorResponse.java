package in.healthconnect.dto.response;

import in.healthconnect.entity.enums.Gender;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorResponse {
private Integer id;
private String doctorCode;
private String firstName;
private String lastName;
private String email;
private String phone;
private Gender gender;
private LocalDate dateOfBirth;
private Integer age;
private String qualification;
private Integer experienceYears;
private BigDecimal consultationFee;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;

}
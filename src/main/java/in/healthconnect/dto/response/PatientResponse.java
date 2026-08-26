package in.healthconnect.dto.response;

import in.healthconnect.entity.enums.BloodGroup;
import in.healthconnect.entity.enums.Gender;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientResponse {
private Integer id;
private String patientCode;
private String firstName;
private String lastName;
private LocalDate dateOfBirth;
private Integer age;
private Gender gender;
private String phone;
private String email;
private String address;
private BloodGroup bloodGroup;
}
package in.healthconnect.dto.request;

import in.healthconnect.entity.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateDoctorRequest {

    @NotBlank
    @Size(max = 50)
private String firstName;

    @NotBlank
    @Size(max = 50)
private String lastName;

    @NotBlank
    @Email
    @Size(max = 100)
private String email;

    @NotBlank
    @Pattern(regexp = "^[0-9]{10,15}$")
private String phone;

    @NotNull
private Gender gender;

    @NotNull
    @Past
private LocalDate dateOfBirth;

    @NotBlank
    @Size(max = 200)
private String qualification;

    @NotNull
    @Min(0)
private Integer experienceYears;

    @NotNull
    @DecimalMin(value = "0.0")
private BigDecimal consultationFee;

}
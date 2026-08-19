package in.healthconnect.dto.request;


import in.healthconnect.entity.enums.BloodGroup;
import in.healthconnect.entity.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePatientRequest {
@NotBlank(message = "First name is required")
@Size(max = 50, message = "First name must not exceed 50 characters")
private String firstName;

@Size(max = 50, message = "Last name must not exceed 50 characters")
private String lastName;

@NotNull(message = "Date of birth is required")
@Past(message = "Date of birth must be a past date")
private LocalDate dateOfBirth;

@NotNull(message = "Gender is required")
private Gender gender;

 @NotBlank(message = "Phone number is required")
 @Pattern(
         regexp = "^[6-9]\\d{9}$",
         message = "Phone number must be a valid 10-digit Indian mobile number"
 )
 private String phone;

@NotBlank(message = "Email is required")
@Email(message = "Please provide a valid email address")
@Size(max = 100, message = "Email must not exceed 100 characters")
private String email;

@Size(max = 500, message = "Address must not exceed 500 characters")
private String address;

private BloodGroup bloodGroup;
 }
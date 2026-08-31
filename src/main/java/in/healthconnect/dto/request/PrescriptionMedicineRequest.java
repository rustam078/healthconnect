package in.healthconnect.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionMedicineRequest {

    @NotBlank(message = "Medicine name is required")
    @Size(max = 150)
    private String medicineName;

    @Size(max = 50)
    private String dosage;

    @Size(max = 50)
    private String frequency;

    @Size(max = 50)
    private String duration;

    @Size(max = 255)
    private String instructions;
}

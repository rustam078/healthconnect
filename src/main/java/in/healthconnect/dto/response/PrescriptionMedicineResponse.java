package in.healthconnect.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionMedicineResponse {

    private Integer id;
    private String medicineName;
    private String dosage;
    private String frequency;
    private String duration;
    private String instructions;
}

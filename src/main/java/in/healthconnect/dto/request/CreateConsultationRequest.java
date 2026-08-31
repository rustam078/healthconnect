package in.healthconnect.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateConsultationRequest {

    @NotBlank(message = "Chief complaint is required")
    private String chiefComplaint;

    private String diagnosis;

    private String notes;

    // @Valid cascades bean validation into each medicine line. The list may be empty:
    // a visit does not always end in a prescription.
    @Valid
    @Builder.Default
    private List<PrescriptionMedicineRequest> medicines = new ArrayList<>();
}

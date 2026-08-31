package in.healthconnect.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationResponse {

    private Integer id;
    private Integer appointmentId;
    private String chiefComplaint;
    private String diagnosis;
    private String notes;

    // One-directional on purpose: the medicine response carries no back-reference to the
    // consultation, so serialising this never loops.
    private List<PrescriptionMedicineResponse> medicines;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

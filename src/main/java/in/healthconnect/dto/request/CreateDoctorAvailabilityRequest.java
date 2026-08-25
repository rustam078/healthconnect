package in.healthconnect.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDoctorAvailabilityRequest {

    @NotEmpty(message = "Availability is required")
    @Valid
    private List<DoctorAvailabilityRequest> availability;
}
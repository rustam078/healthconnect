package in.healthconnect.dto.request;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignDoctorSpecialtiesRequest(

@NotEmpty(message = "At least one specialty is required")
List<@NotNull Integer> specialtyIds
) {
}
package in.healthconnect.dto.response;

import java.util.List;

public record AssignDoctorSpecialtiesResponse(
        DoctorResponse doctor,
        List<SpecialtyResponse> specialties,
        List<DoctorAvailabilityResponse> availabilityToSave
) {
}
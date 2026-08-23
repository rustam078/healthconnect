package in.healthconnect.utils;

import in.healthconnect.dto.response.SpecialtyResponse;
import in.healthconnect.entity.Specialty;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
public class SpecialityUtils {

    public static SpecialtyResponse mapToSpecialityResponse(Specialty  specialty) {

        final SpecialtyResponse specialtyResponse = new SpecialtyResponse();
        specialtyResponse.setId(specialty.getId());
        specialtyResponse.setName(specialty.getName());
        specialtyResponse.setDescription(specialty.getDescription());
        specialtyResponse.setCreatedAt(specialty.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime());
        specialtyResponse.setUpdatedAt(specialty.getUpdatedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime());

        return specialtyResponse;
    }
}

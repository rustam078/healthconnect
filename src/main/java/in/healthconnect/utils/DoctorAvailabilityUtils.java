package in.healthconnect.utils;

import in.healthconnect.dto.response.DoctorAvailabilityResponse;
import in.healthconnect.entity.DoctorAvailability;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DoctorAvailabilityUtils {

    public static DoctorAvailabilityResponse mapToResponse(DoctorAvailability doctorAvailability) {
      return  DoctorAvailabilityResponse.builder()
                .id(doctorAvailability.getId())
                .dayOfWeek(doctorAvailability.getDayOfWeek())
                .startTime(doctorAvailability.getStartTime())
                .endTime(doctorAvailability.getEndTime())
                .breakStartTime(doctorAvailability.getBreakStartTime())
                .breakEndTime(doctorAvailability.getBreakEndTime())
                .build();

    }
}

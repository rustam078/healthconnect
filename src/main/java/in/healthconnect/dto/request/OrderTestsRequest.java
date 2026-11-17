package in.healthconnect.dto.request;

import java.util.List;

// The catalogue test ids the doctor is ordering for an appointment.
public record OrderTestsRequest(List<Integer> labTestIds) {
}

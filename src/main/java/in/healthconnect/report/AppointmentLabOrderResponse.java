package in.healthconnect.report;

import in.healthconnect.entity.enums.BloodGroup;
import in.healthconnect.entity.enums.Gender;

import java.time.LocalDate;
import java.util.List;

// One appointment's lab orders, for the admin reports screen. `status` is the appointment
// status when every test is available, otherwise "ORDERED" (work still pending).
public record AppointmentLabOrderResponse(Integer appointmentId, Integer patientId, String patientName,
                                          LocalDate appointmentDate, Integer age, Gender gender,
                                          BloodGroup bloodGroup, String status, List<LabOrderItem> labOrders) {
}

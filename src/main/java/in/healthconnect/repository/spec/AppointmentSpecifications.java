package in.healthconnect.repository.spec;

import in.healthconnect.dto.request.AppointmentSearchFilter;
import in.healthconnect.entity.Appointment;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

// Turns the optional admin search filters into one JPA Specification: unset filters are skipped,
// soft-deleted rows are always excluded.
public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    // Build the combined predicate from whichever filters the caller provided.
    public static Specification<Appointment> fromFilter(AppointmentSearchFilter f) {
        List<Specification<Appointment>> parts = new ArrayList<>();
        parts.add((root, query, cb) -> cb.isFalse(root.get("deleted")));
        addIf(parts, f.getAppointmentId(), (root, query, cb) -> cb.equal(root.get("id"), f.getAppointmentId()));
        addIf(parts, f.getDoctorId(), (root, query, cb) -> cb.equal(root.get("doctor").get("id"), f.getDoctorId()));
        addIf(parts, f.getPatientId(), (root, query, cb) -> cb.equal(root.get("patient").get("id"), f.getPatientId()));
        addIf(parts, f.getStatus(), (root, query, cb) -> cb.equal(root.get("status"), f.getStatus()));
        addIf(parts, f.getDateFrom(), (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("appointmentDate"), f.getDateFrom()));
        addIf(parts, f.getDateTo(), (root, query, cb) -> cb.lessThanOrEqualTo(root.get("appointmentDate"), f.getDateTo()));
        if (f.getPatientName() != null && !f.getPatientName().isBlank()) {
            parts.add(patientNameLike(f.getPatientName().trim()));
        }
        return Specification.allOf(parts);
    }

    private static void addIf(List<Specification<Appointment>> parts, Object value, Specification<Appointment> part) {
        if (value != null) {
            parts.add(part);
        }
    }

    // Matches the text against the patient's first name, last name, or "first last".
    private static Specification<Appointment> patientNameLike(String name) {
        String like = "%" + name.toLowerCase() + "%";
        return (root, query, cb) -> {
            var patient = root.get("patient");
            var full = cb.lower(cb.concat(cb.concat(patient.get("firstName"), " "), patient.get("lastName")));
            return cb.or(cb.like(cb.lower(patient.get("firstName")), like),
                    cb.like(cb.lower(patient.get("lastName")), like), cb.like(full, like));
        };
    }
}

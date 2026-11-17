package in.healthconnect.portal;

import in.healthconnect.security.model.DataScope;

import java.util.List;

// The logged-in user's data reach, resolved once per request: their scope, own ids, and
// (for a doctor) the specialty ids that make up their department.
public record AccessScope(DataScope scope, Integer patientId, Integer doctorId, List<Integer> specialtyIds) {
}

package in.healthconnect.security.dto;

// Admin updates a user. Any null field is left unchanged; a blank password means "keep it".
public record UpdateUserRequest(Integer roleId, Boolean enabled, String password,
                                Integer doctorId, Integer patientId) {
}

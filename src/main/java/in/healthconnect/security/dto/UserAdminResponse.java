package in.healthconnect.security.dto;

import in.healthconnect.security.entity.User;

// A user as the admin Users screen shows it (never the password).
public record UserAdminResponse(Integer id, String username, String role, Integer roleId,
                                boolean enabled, Integer doctorId, Integer patientId, Integer avatarFileId) {

    public static UserAdminResponse from(User user) {
        return new UserAdminResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().getName(),
                user.getRole().getId(),
                user.isEnabled(),
                user.getDoctorId(),
                user.getPatientId(),
                user.getAvatarFileId());
    }
}

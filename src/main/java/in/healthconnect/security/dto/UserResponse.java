package in.healthconnect.security.dto;

import in.healthconnect.security.entity.User;

// The logged-in user, as the frontend needs it (no password).
public record UserResponse(Integer id, String username, String role, Integer doctorId, Integer patientId,
                           Integer avatarFileId) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().getName(),
                user.getDoctorId(),
                user.getPatientId(),
                user.getAvatarFileId());
    }
}

package in.healthconnect.community.service;

import in.healthconnect.community.entity.enums.UserType;
import in.healthconnect.security.entity.User;
import org.springframework.stereotype.Component;

// Works out whether an account acts as a doctor, a patient, or admin/staff, from its portal links.
@Component
public class UserTypeResolver {

    public UserType of(User user) {
        if (user.getDoctorId() != null) {
            return UserType.DOCTOR;
        }
        if (user.getPatientId() != null) {
            return UserType.PATIENT;
        }
        return UserType.ADMIN;
    }
}

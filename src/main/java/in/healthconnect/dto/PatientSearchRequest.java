package in.healthconnect.dto;

import in.healthconnect.entity.enums.BloodGroup;
import in.healthconnect.entity.enums.Gender;

public record PatientSearchRequest(

        String search,

        String firstName,

        Gender gender,

        BloodGroup bloodGroup

) {
}
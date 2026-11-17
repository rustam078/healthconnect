package in.healthconnect.entity.projection;

import in.healthconnect.entity.enums.BloodGroup;
import in.healthconnect.entity.enums.Gender;
import in.healthconnect.entity.enums.LabTestCategory;
import in.healthconnect.entity.enums.OrderedTestStatus;

import java.time.Instant;
import java.time.LocalDate;

public interface AppointmentLabOrderProjection {

    Integer getAppointmentId();
    Integer getPatientId();
    String getFirstName();
    String getLastName();

    LocalDate getAppointmentDate();
    LocalDate getDateOfBirth();
    Gender getGender();
    BloodGroup getBloodGroup();

    String getAppointmentStatus();

    Integer getOrderedTestId();
    Integer getReportId();
    LabTestCategory getCategory();
    OrderedTestStatus getLabStatus();
    String getTestName();

    String getOriginalFileName();
    String getContentType();
    Long getFileSizeBytes();
    Instant getCreatedAt();
}
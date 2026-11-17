package in.healthconnect.report;

import in.healthconnect.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

// Metadata for one report file an admin uploaded for a patient. The bytes live on disk under
// storedFileName; everything else about the file is here.
@Entity
@Table(name = "patient_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE patient_report SET is_deleted = true WHERE id = ?")
public class PatientReport extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Integer patientId;

    // Optional: the appointment this report belongs to, so a visit's record shows it in one place.
    @Column(name = "appointment_id")
    private Integer appointmentId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    // The unique name on disk (UUID). Never shown to the user.
    @Column(name = "stored_file_name", nullable = false, unique = true, length = 100)
    private String storedFileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "uploaded_by_user_id")
    private Integer uploadedByUserId;
}

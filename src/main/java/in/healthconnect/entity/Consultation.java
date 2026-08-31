package in.healthconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "consultations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE consultations SET is_deleted = true WHERE id = ?")
public class Consultation extends BaseEntity {

    // One visit per appointment. unique keeps a second consultation off the same
    // appointment at the database level, not just in service code.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_consultation_appointment"))
    private Appointment appointment;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // The prescribed medicines. cascade + orphanRemoval mean the lines live and die
    // with their consultation; @OrderBy keeps them in the order they were entered.
    @OneToMany(mappedBy = "consultation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PrescriptionMedicine> medicines = new ArrayList<>();

    // Keeps both sides of the relationship in step, so the foreign key on the medicine
    // is always set before the cascade saves it.
    public void addMedicine(PrescriptionMedicine medicine) {
        medicine.setConsultation(this);
        this.medicines.add(medicine);
    }
}

package in.healthconnect.service;

import in.healthconnect.dto.request.CreateConsultationRequest;
import in.healthconnect.dto.response.ConsultationResponse;
import in.healthconnect.dto.response.PrescriptionMedicineResponse;
import in.healthconnect.entity.Appointment;
import in.healthconnect.entity.Consultation;
import in.healthconnect.entity.PrescriptionMedicine;
import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.exception.AppointmentConflictException;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.repository.ConsultationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultationService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;

    // Record what happened at the visit. The consultation and its medicines ride one
    // transaction: either the whole record lands or none of it does. Completing the
    // appointment is a separate, deliberate action (the "Mark completed" button), so this
    // leaves the appointment's status untouched.
    @Transactional
    public ConsultationResponse createConsultation(Integer appointmentId, CreateConsultationRequest request) {

        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow(() ->
                new ResourceNotFoundException("Appointment with id '" + appointmentId + "' does not exist"));

        // A cancelled slot never happened, so there is nothing to write up. A scheduled or
        // an already-completed appointment can both take a consultation.
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new AppointmentConflictException(
                    "A consultation cannot be recorded for a cancelled appointment.");
        }

        // Belt to the unique constraint's braces: a friendly error instead of a raw DB
        // violation if a consultation already exists for this appointment.
        if (consultationRepository.existsByAppointmentId(appointmentId)) {
            throw new AppointmentConflictException(
                    "A consultation has already been recorded for this appointment.");
        }

        Consultation consultation = Consultation.builder()
                .appointment(appointment)
                .chiefComplaint(request.getChiefComplaint())
                .diagnosis(request.getDiagnosis())
                .notes(request.getNotes())
                .build();

        request.getMedicines().forEach(m -> consultation.addMedicine(
                PrescriptionMedicine.builder()
                        .medicineName(m.getMedicineName())
                        .dosage(m.getDosage())
                        .frequency(m.getFrequency())
                        .duration(m.getDuration())
                        .instructions(m.getInstructions())
                        .build()));

        Consultation saved = consultationRepository.save(consultation);

        return mapToResponse(saved);
    }

    // The visit record for one appointment, medicines and all, for later review.
    @Transactional
    public ConsultationResponse getByAppointmentId(Integer appointmentId) {

        Consultation consultation = consultationRepository.findByAppointmentId(appointmentId).orElseThrow(() ->
                new ResourceNotFoundException(
                        "No consultation recorded for appointment id '" + appointmentId + "'"));

        return mapToResponse(consultation);
    }

    private ConsultationResponse mapToResponse(Consultation consultation) {

        List<PrescriptionMedicineResponse> medicines = consultation.getMedicines().stream()
                .map(m -> PrescriptionMedicineResponse.builder()
                        .id(m.getId())
                        .medicineName(m.getMedicineName())
                        .dosage(m.getDosage())
                        .frequency(m.getFrequency())
                        .duration(m.getDuration())
                        .instructions(m.getInstructions())
                        .build())
                .toList();

        return ConsultationResponse.builder()
                .id(consultation.getId())
                .appointmentId(consultation.getAppointment().getId())
                .chiefComplaint(consultation.getChiefComplaint())
                .diagnosis(consultation.getDiagnosis())
                .notes(consultation.getNotes())
                .medicines(medicines)
                .createdAt(consultation.getCreatedAt().atZone(IST).toLocalDateTime())
                .updatedAt(consultation.getUpdatedAt().atZone(IST).toLocalDateTime())
                .build();
    }
}

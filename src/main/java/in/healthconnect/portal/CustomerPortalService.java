package in.healthconnect.portal;

import in.healthconnect.dto.request.CreateAppointmentRequest;
import in.healthconnect.dto.response.AppointmentResponse;
import in.healthconnect.dto.response.ConsultationResponse;
import in.healthconnect.dto.response.SpecialtyResponse;
import in.healthconnect.entity.Appointment;
import in.healthconnect.entity.Doctor;
import in.healthconnect.entity.DoctorAvailability;
import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.portal.SlotService.BookedRange;
import in.healthconnect.portal.SlotService.SlotWindow;
import in.healthconnect.report.ReportResponse;
import in.healthconnect.report.ReportService;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.repository.DoctorAvailabilityRepository;
import in.healthconnect.repository.DoctorSpecialtyRepository;
import in.healthconnect.service.AppointmentService;
import in.healthconnect.service.ConsultationPdfService;
import in.healthconnect.service.ConsultationService;
import in.healthconnect.service.SpecialityService;
import in.healthconnect.utils.AppointmentUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// Everything the patient portal does, always scoped to the logged-in patient.
@Service
@RequiredArgsConstructor
public class CustomerPortalService {

    private static final int SLOT_MINUTES = 30;

    private final CurrentUserProvider currentUser;
    private final SlotService slotService;
    private final DoctorSpecialtyRepository doctorSpecialtyRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final ConsultationService consultationService;
    private final ConsultationPdfService consultationPdfService;
    private final ReportService reportService;
    private final SpecialityService specialityService;

    // Specialties for the patient's doctor search (portal-gated, so no admin permission needed).
    @Transactional(readOnly = true)
    public List<SpecialtyResponse> specialties() {
        return specialityService.getSpecialties(null, null, PageRequest.of(0, 200)).getContent();
    }

    // Availability-first: only doctors with an open slot on that date for that specialty.
    @Transactional(readOnly = true)
    public List<AvailableDoctorResponse> availableDoctors(Integer specialtyId, LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        LocalTime notBefore = date.isEqual(LocalDate.now()) ? LocalTime.now() : null;
        List<AvailableDoctorResponse> result = new ArrayList<>();
        for (var map : doctorSpecialtyRepository.findBySpecialtyId(specialtyId)) {
            Doctor doctor = map.getDoctor();
            List<LocalTime> slots = slotsFor(doctor.getId(), day, date, notBefore);
            if (!slots.isEmpty()) {
                result.add(new AvailableDoctorResponse(doctor.getId(),
                        doctor.getFirstName() + " " + doctor.getLastName(),
                        doctor.getQualification(), doctor.getConsultationFee(), slots,
                        occupiedSlotsFor(doctor.getId(), day, slots)));
            }
        }
        return result;
    }

    public AppointmentResponse book(BookAppointmentRequest request) {
        CreateAppointmentRequest create = CreateAppointmentRequest.builder()
                .patientId(currentUser.requirePatientId())
                .doctorId(request.doctorId())
                .appointmentDate(request.date())
                .startTime(request.startTime())
                .durationMinutes(SLOT_MINUTES)
                .appointmentType("NEW")
                .reason(request.reason())
                .build();
        return appointmentService.createAppointment(create);
    }

    // The grid slots on that day that are NOT free (booked or past), for the red "occupied" cells.
    private List<LocalTime> occupiedSlotsFor(Integer doctorId, DayOfWeek day, List<LocalTime> free) {
        java.util.Set<LocalTime> freeSet = new java.util.HashSet<>(free);
        List<LocalTime> all = new ArrayList<>();
        for (DoctorAvailability window : availabilityRepository.findByDoctorIdAndDayOfWeek(doctorId, day)) {
            all.addAll(slotService.freeSlots(new SlotWindow(window.getStartTime(), window.getEndTime(),
                    window.getBreakStartTime(), window.getBreakEndTime(), SLOT_MINUTES), List.of(), null));
        }
        return all.stream().distinct().filter(t -> !freeSet.contains(t)).sorted().toList();
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> myAppointments(Pageable pageable) {
        return appointmentRepository
                .findByPatientIdOrderByAppointmentDateDescStartTimeDesc(currentUser.requirePatientId(), pageable)
                .map(AppointmentUtils::mapToAppointmentResponse);
    }

    @Transactional
    public void cancel(Integer appointmentId) {
        assertMyAppointment(appointmentId);
        appointmentService.cancelAppointment(appointmentId);
    }

    @Transactional(readOnly = true)
    public ConsultationResponse consultation(Integer appointmentId) {
        assertMyAppointment(appointmentId);
        return consultationService.getByAppointmentId(appointmentId);
    }

    @Transactional(readOnly = true)
    public byte[] consultationPdf(Integer appointmentId) {
        assertMyAppointment(appointmentId);
        return consultationPdfService.renderByAppointmentId(appointmentId);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> myReports() {
        return reportService.listByPatient(currentUser.requirePatientId());
    }

    public ReportService.ReportFile reportFile(Integer reportId) {
        return reportService.getFileForPatient(reportId, currentUser.requirePatientId());
    }

    private List<LocalTime> slotsFor(Integer doctorId, DayOfWeek day, LocalDate date, LocalTime notBefore) {
        List<DoctorAvailability> windows = availabilityRepository.findByDoctorIdAndDayOfWeek(doctorId, day);
        if (windows.isEmpty()) {
            return List.of();
        }
        List<BookedRange> booked = bookedRanges(doctorId, date);
        List<LocalTime> slots = new ArrayList<>();
        for (DoctorAvailability window : windows) {
            SlotWindow slotWindow = new SlotWindow(window.getStartTime(), window.getEndTime(),
                    window.getBreakStartTime(), window.getBreakEndTime(), SLOT_MINUTES);
            slots.addAll(slotService.freeSlots(slotWindow, booked, notBefore));
        }
        return slots.stream().distinct().sorted().toList();
    }

    private List<BookedRange> bookedRanges(Integer doctorId, LocalDate date) {
        return appointmentRepository
                .findByDoctorIdAndAppointmentDateAndStatusNot(doctorId, date, AppointmentStatus.CANCELLED)
                .stream().map(a -> new BookedRange(a.getStartTime(), a.getEndTime())).toList();
    }

    // The appointment must belong to me, or it is "not found" as far as this patient is concerned.
    private void assertMyAppointment(Integer appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
        if (!appointment.getPatient().getId().equals(currentUser.requirePatientId())) {
            throw new ResourceNotFoundException("Appointment not found: " + appointmentId);
        }
    }
}

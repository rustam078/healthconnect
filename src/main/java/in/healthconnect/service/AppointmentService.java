package in.healthconnect.service;

import in.healthconnect.dto.request.CreateAppointmentRequest;
import in.healthconnect.dto.response.*;
import in.healthconnect.entity.*;
import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.exception.AppointmentConflictException;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.repository.DoctorAvailabilityRepository;
import in.healthconnect.repository.DoctorRepository;
import in.healthconnect.repository.PatientRepository;
import in.healthconnect.utils.AppointmentUtils;
import in.healthconnect.utils.CommonUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorAvailabilityRepository doctorAvailabilityRepository;

    @Transactional
    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {
        if (request.getPatientId() == null || request.getPatientId() <= 0) {
            throw new IllegalArgumentException("Patient ID is required and must be positive");
        }

        //patient find
        Patient patient = patientRepository.findById(request.getPatientId()).orElseThrow(() ->
                new ResourceNotFoundException("Patient with id '" + request.getPatientId() + "' does not exist"));

         //doctor find
        Doctor doctor = doctorRepository.findActiveDoctorForUpdate(request.getDoctorId()).orElseThrow(() ->
                new ResourceNotFoundException("Doctor with id '" + request.getDoctorId() + "' does not exist"));

        //end time cal
        LocalTime endTime = request.getStartTime().plusMinutes(request.getDurationMinutes());

        // FIRST → date/time validation
         CommonUtils.validateDateAndTime(request.getAppointmentDate(), request.getStartTime());

        validateDoctorAvailability(doctor,request.getAppointmentDate(),request.getStartTime(),endTime);

        validateAppointmentConflict(request.getDoctorId(), request.getAppointmentDate(), request.getStartTime(), endTime);


        //create appointment
        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .appointmentDate(request.getAppointmentDate())
                .startTime(request.getStartTime())
                .endTime(endTime)
                .durationMinutes(request.getDurationMinutes())
                .status(AppointmentStatus.SCHEDULED)
                .build();

        //save appointment

        Appointment savedAppointment =
                appointmentRepository.save(appointment);

        //return response
      return AppointmentUtils.mapToAppointmentResponse(savedAppointment);
    }


    // Move an appointment to another status.
    //
    // SCHEDULED is the only live state; the other two are ends of the road. A completed
    // visit cannot be un-taken, and a cancelled one has already handed its slot back, so
    // reviving it could double-book the doctor - booking again is a new appointment.
    @Transactional
    public AppointmentResponse updateStatus(Integer appointmentId, AppointmentStatus target) {

        Appointment appointment = findActiveAppointment(appointmentId);
        changeStatus(appointment, target);

        return AppointmentUtils.mapToAppointmentResponse(appointmentRepository.save(appointment));
    }

    // Move an appointment to a different doctor and/or a different time.
    //
    // The doctor row is locked FOR UPDATE first, exactly as booking does. That lock is what
    // stops two people rescheduling into the same free slot at the same moment: without it
    // both would run the overlap check while the other's row was still uncommitted, both
    // would see a free slot, and both would save. Serialising on the doctor means the
    // second one runs its check after the first has committed, and loses.
    @Transactional
    public AppointmentResponse rescheduleAppointment(Integer appointmentId, CreateAppointmentRequest request) {

        Appointment appointment = findActiveAppointment(appointmentId);

        // Only a live appointment can be moved: a cancelled slot is back on offer and a
        // completed visit has already happened.
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new AppointmentConflictException(
                    "An appointment that is " + appointment.getStatus() + " cannot be moved.");
        }

        Doctor doctor = doctorRepository.findActiveDoctorForUpdate(request.getDoctorId()).orElseThrow(() ->
                new ResourceNotFoundException("Doctor with id '" + request.getDoctorId() + "' does not exist"));

        LocalTime endTime = request.getStartTime().plusMinutes(request.getDurationMinutes());

        CommonUtils.validateDateAndTime(request.getAppointmentDate(), request.getStartTime());

        validateDoctorAvailability(doctor, request.getAppointmentDate(), request.getStartTime(), endTime);

        boolean conflict = appointmentRepository.existsOverlappingAppointmentExcluding(
                request.getDoctorId(), request.getAppointmentDate(), request.getStartTime(), endTime,
                AppointmentStatus.CANCELLED, appointmentId);
        if (conflict) {
            throw new AppointmentConflictException("Doctor already has an appointment during the requested time");
        }

        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(endTime);
        appointment.setDurationMinutes(request.getDurationMinutes());

        return AppointmentUtils.mapToAppointmentResponse(appointmentRepository.save(appointment));
    }

    // Call an appointment off.
    //
    // Two things happen, and both matter. The status becomes CANCELLED so the slot is free
    // again - the overlap check ignores cancelled rows. The row is then soft-deleted
    // (@SQLDelete on the entity sets is_deleted), so it drops out of every normal query
    // while the record of it having existed survives.
    @Transactional
    public void cancelAppointment(Integer appointmentId) {

        Appointment appointment = findActiveAppointment(appointmentId);

        changeStatus(appointment, AppointmentStatus.CANCELLED);

        // Saved before the delete: @SQLDelete only writes is_deleted, so a status change
        // left pending here would never reach the database.
        appointmentRepository.saveAndFlush(appointment);
        appointmentRepository.delete(appointment);
    }

    // Every legal status move, in one table. Both terminal states map to an empty set, so
    // they refuse everything - including a repeat of themselves.
    private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED = Map.of(
            AppointmentStatus.SCHEDULED, EnumSet.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED),
            AppointmentStatus.COMPLETED, EnumSet.noneOf(AppointmentStatus.class),
            AppointmentStatus.CANCELLED, EnumSet.noneOf(AppointmentStatus.class));

    private void changeStatus(Appointment appointment, AppointmentStatus target) {

        Set<AppointmentStatus> allowed = ALLOWED.get(appointment.getStatus());

        // getOrDefault would quietly treat an unknown status as "nothing is allowed"; being
        // loud about it means a status added to the enum but not to the table above shows
        // up the first time someone tries to move it, not months later.
        if (allowed == null) {
            throw new IllegalArgumentException(
                    "No transitions are defined for status " + appointment.getStatus() + ".");
        }
        if (!allowed.contains(target)) {
            throw new AppointmentConflictException(
                    "Cannot move from " + appointment.getStatus() + " to " + target + ".");
        }
        appointment.setStatus(target);
    }

    private Appointment findActiveAppointment(Integer appointmentId) {
        return appointmentRepository.findById(appointmentId).orElseThrow(() ->
                new ResourceNotFoundException("Appointment with id '" + appointmentId + "' does not exist"));
    }


    private void validateDoctorAvailability(Doctor doctor, LocalDate appointmentDate, LocalTime startTime, LocalTime endTime) {

        DayOfWeek dayOfWeek = appointmentDate.getDayOfWeek();

        List<DoctorAvailability> availabilities = doctorAvailabilityRepository.findByDoctorIdAndDayOfWeek(doctor.getId(), dayOfWeek);

        boolean available = availabilities.stream().anyMatch(availability -> !startTime.isBefore(availability.getStartTime()) && !endTime.isAfter(availability.getEndTime()));
        if (!available) {
            throw new IllegalArgumentException("Doctor is not available for the requested time");
        }

        boolean duringBreak = availabilities.stream().filter(availability ->
                availability.getBreakStartTime() != null && availability.getBreakEndTime() != null)
                .anyMatch(availability -> startTime.isBefore(availability.getBreakEndTime()) && endTime.isAfter(availability.getBreakStartTime()));
        if (duringBreak) {
            throw new IllegalArgumentException("Doctor is on break during the requested appointment time");
        }
    }


    private void validateAppointmentConflict(Integer doctorId, LocalDate appointmentDate, LocalTime startTime, LocalTime endTime) {

        boolean conflict = appointmentRepository.existsOverlappingAppointment(doctorId, appointmentDate, startTime, endTime, AppointmentStatus.CANCELLED);

        if (conflict) {
            throw new AppointmentConflictException("Doctor already has an appointment during the requested time");
        }
    }


    public Page<AppointmentResponse> getAllAppointmentsByDoctorId(Integer doctorId,LocalDate appointmentDate, Pageable pageable) {

        doctorRepository.findById(doctorId).orElseThrow(() -> new ResourceNotFoundException("Doctor with id '" + doctorId + "' does not exist"));
        Page<Appointment> appointments;
        if(appointmentDate != null) {
             appointments = appointmentRepository.findByDoctorIdAndAppointmentDate(doctorId, appointmentDate, pageable);
        }else {
            appointments =  appointmentRepository.findByDoctorId(doctorId, pageable);
        }

        return appointments.map(AppointmentUtils::mapToAppointmentResponse);
    }
}

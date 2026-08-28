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
import java.util.List;


@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorAvailabilityRepository doctorAvailabilityRepository;

    @Transactional
    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {

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

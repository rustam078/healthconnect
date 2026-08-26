package in.healthconnect.service;

import in.healthconnect.dto.request.CreateAppointmentRequest;
import in.healthconnect.dto.response.AppointmentResponse;
import in.healthconnect.entity.Appointment;
import in.healthconnect.entity.Doctor;
import in.healthconnect.entity.DoctorAvailability;
import in.healthconnect.entity.Patient;
import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.repository.DoctorAvailabilityRepository;
import in.healthconnect.repository.DoctorRepository;
import in.healthconnect.repository.PatientRepository;
import in.healthconnect.utils.AppointmentUtils;
import lombok.RequiredArgsConstructor;
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

    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {

        //end time cal
        LocalTime endTime = request.getStartTime().plusMinutes(request.getDurationMinutes());

        validateDoctorAvailability(doctorRepository.findById(request.getDoctorId()).get(),request.getAppointmentDate(),request.getStartTime(),endTime);

        //patient find
        Patient patient = patientRepository.findById(request.getPatientId()).orElseThrow(() ->
                new ResourceNotFoundException("Patient with id '" + request.getPatientId() + "' does not exist"));

         //doctor find
        Doctor doctor = doctorRepository.findById(request.getDoctorId()).orElseThrow(() ->
                new ResourceNotFoundException("Doctor with id '" + request.getDoctorId() + "' does not exist"));

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

        boolean duringBreak = availabilities.stream().anyMatch(availability -> startTime.isBefore(availability.getBreakEndTime()) && endTime.isAfter(availability.getBreakStartTime()));
        if (duringBreak) {
            throw new IllegalArgumentException("Doctor is on break during the requested appointment time");
        }
    }


    private void validateAppointmentConflict(Integer doctorId, LocalDate appointmentDate, LocalTime startTime, LocalTime endTime) {

        boolean conflict = appointmentRepository.existsOverlappingAppointment(doctorId, appointmentDate, startTime, endTime, AppointmentStatus.CANCELLED);

        if (conflict) {
            throw new IllegalArgumentException("Doctor already has an appointment during the requested time");
        }
    }

}

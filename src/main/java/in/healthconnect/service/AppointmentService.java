package in.healthconnect.service;

import in.healthconnect.dto.request.CreateAppointmentRequest;
import in.healthconnect.dto.response.AppointmentResponse;
import in.healthconnect.entity.Appointment;
import in.healthconnect.entity.Doctor;
import in.healthconnect.entity.Patient;
import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.repository.DoctorRepository;
import in.healthconnect.repository.PatientRepository;
import in.healthconnect.utils.AppointmentUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;

    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {

        //patient find
        Patient patient = patientRepository.findById(request.getPatientId()).orElseThrow(() ->
                new ResourceNotFoundException("Patient with id '" + request.getPatientId() + "' does not exist"));

         //doctor find
        Doctor doctor = doctorRepository.findById(request.getDoctorId()).orElseThrow(() ->
                new ResourceNotFoundException("Doctor with id '" + request.getDoctorId() + "' does not exist"));

        //end time cal
        LocalTime endTime = request.getStartTime().plusMinutes(request.getDurationMinutes());

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
}

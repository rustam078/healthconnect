package in.healthconnect.service;

import in.healthconnect.specificationFilter.PatientSpecification;
import in.healthconnect.dto.request.PatientSearchRequest;
import in.healthconnect.dto.request.CreatePatientRequest;
import in.healthconnect.dto.response.AppointmentResponse;
import in.healthconnect.dto.response.PatientDetailResponse;
import in.healthconnect.dto.response.PatientResponse;
import in.healthconnect.entity.Patient;
import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.utils.AppointmentUtils;
import in.healthconnect.utils.PatientUtils;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class PatientService {

    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private PatientUtils patientUtils;
    // Read-only here: the patient record page counts and lists a patient's appointments,
    // it never creates or changes one. Booking stays in AppointmentService.
    @Autowired
    private AppointmentRepository appointmentRepository;

    public PatientResponse createPatient(CreatePatientRequest request) {
        //step1  Check duplicate email
        if (patientRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
            throw new IllegalArgumentException("Patient with email '" + request.getEmail() + "' already exists");
        }
        //step2  Generate unique patient code shown below
        String patientCode = patientUtils.generatePatientCode();

        //step3 create patient objects using builder
        Patient patient = Patient.builder()
                .patientCode(patientCode)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .bloodGroup(request.getBloodGroup())
                .build();


        //step4 save that patient object
        patient = patientRepository.save(patient);
        //step5 retun PatientResponse
        return PatientUtils.mapToPatientResponse(patient);
    }


//    public Page<PatientResponse> getAllPatientOnPage(Pageable pageable, Integer patientId) {
//        if (patientId != null) {
//            Optional<Patient> patientOptional = patientRepository.findById(patientId);
//            if (patientOptional.isEmpty()) {
//                return Page.empty(pageable);
//            }
//            PatientResponse response = PatientUtils.mapToPatientResponse(patientOptional.get());
//            return new PageImpl<>(List.of(response), pageable, 1);
//        }
//        Page<Patient> patientPage = patientRepository.findAll(pageable);
//        return patientPage.map(PatientUtils::mapToPatientResponse);
//
//    }




    public Page<PatientResponse> searchPatients(
            PatientSearchRequest request,
            Integer patientId,
            Pageable pageable
    ) {

        if (patientId != null) {
            Optional<Patient> patientOptional = patientRepository.findById(patientId);
            if (patientOptional.isEmpty()) {return Page.empty(pageable);}
            PatientResponse response = PatientUtils.mapToPatientResponse(patientOptional.get());
            return new PageImpl<>(List.of(response), pageable, 1);
        }

        Page<Patient> patients = patientRepository.findAll(
                PatientSpecification.search(request),
                pageable
        );

        return patients.map(PatientUtils::mapToPatientResponse);
    }

    public PatientResponse updateExistPatientById(Integer id ,CreatePatientRequest patientRequest) {

        Patient patient = patientRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Patient with id '" + id + "' does not exist"));

        if(patientRequest.getPhone() != null) {
            patient.setPhone(patientRequest.getPhone().trim());
        }
        if(patientRequest.getEmail() != null){
            patient.setEmail(patientRequest.getEmail().trim());
        }
        if(patientRequest.getBloodGroup() != null){
            patient.setBloodGroup(patientRequest.getBloodGroup());
        }
        if(patientRequest.getAddress() != null){
            patient.setAddress(patientRequest.getAddress().trim());
        }

        patientRepository.save(patient);
        return PatientUtils.mapToPatientResponse(patient);
    }

    public void deletePatientById(Integer id) {
        Patient patient = patientRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Patient with id '" + id + "' does not exist"));

//        patient.setDeleted(true);
        patientRepository.delete(patient);
    }

    // ---------- patient record page ----------

    @Transactional(readOnly = true)
    public PatientDetailResponse getPatientDetails(Integer id) {
        Patient patient = patientRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Patient with id '" + id + "' does not exist"));

        LocalDate today = LocalDate.now();
        PatientDetailResponse.Stats stats = new PatientDetailResponse.Stats(
                // Every appointment on file, so this matches the history table below it.
                appointmentRepository.countByPatientId(id),
                // "Upcoming" means still going to happen: today or later AND not already
                appointmentRepository.countUpcoming(id, today, AppointmentStatus.SCHEDULED),
                appointmentRepository.countDistinctDoctorsSeen(id, AppointmentStatus.COMPLETED, today),
                appointmentRepository.findLastVisitDate(id, AppointmentStatus.COMPLETED, today));

        return new PatientDetailResponse(PatientUtils.mapToPatientResponse(patient), stats);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getPatientAppointments(Integer id, Pageable pageable) {
        if (!patientRepository.existsById(id)) {
            throw new ResourceNotFoundException("Patient with id '" + id + "' does not exist");
        }
        return appointmentRepository
                .findByPatientIdOrderByAppointmentDateDescStartTimeDesc(id, pageable)
                .map(AppointmentUtils::mapToAppointmentResponse);
    }
}
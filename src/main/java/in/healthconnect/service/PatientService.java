package in.healthconnect.service;

import in.healthconnect.dto.request.CreatePatientRequest;
import in.healthconnect.dto.response.PatientResponse;
import in.healthconnect.entity.Patient;
import in.healthconnect.utils.PatientUtils;
import in.healthconnect.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class PatientService {

    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private PatientUtils patientUtils;

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


    public Page<PatientResponse> getAllPatientOnPage(Pageable pageable) {
        Page<Patient> patientPage = patientRepository.findAll(pageable);
        return patientPage.map(PatientUtils::mapToPatientResponse);
    }


}
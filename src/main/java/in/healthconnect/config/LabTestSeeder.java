package in.healthconnect.config;

import in.healthconnect.entity.LabTest;
import in.healthconnect.entity.enums.LabTestCategory;
import in.healthconnect.repository.LabTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

// Seeds the lab-test catalogue on first run so doctors have tests to order. Dev convenience.
@Component
@Order(4)
@RequiredArgsConstructor
public class LabTestSeeder implements CommandLineRunner {

    private static final List<String> LABORATORY = List.of(
            "Complete Blood Count (CBC)", "Lipid Profile", "Liver Function Test (LFT)",
            "Kidney Function Test (KFT)", "Blood Sugar Fasting", "Blood Sugar PP", "HbA1c",
            "Thyroid Profile (TSH/T3/T4)", "Urine Routine", "Vitamin D", "Vitamin B12",
            "Serum Electrolytes", "C-Reactive Protein (CRP)", "ESR", "Dengue NS1",
            "Malaria Antigen", "Widal Test", "COVID-19 RT-PCR", "Hemoglobin (Hb)", "Platelet Count");

    private static final List<String> RADIOLOGY = List.of(
            "Chest X-Ray", "X-Ray Spine", "Ultrasound Abdomen", "Ultrasound Pelvis",
            "MRI Brain", "CT Scan Chest", "ECG", "2D Echocardiography");

    private final LabTestRepository labTestRepository;

    @Override
    public void run(String... args) {
        if (labTestRepository.count() > 0) {
            return;
        }
        LABORATORY.forEach(name -> labTestRepository.save(build(name, LabTestCategory.LABORATORY)));
        RADIOLOGY.forEach(name -> labTestRepository.save(build(name, LabTestCategory.RADIOLOGY)));
    }

    private LabTest build(String name, LabTestCategory category) {
        return LabTest.builder().name(name).category(category).active(true).build();
    }
}

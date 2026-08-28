package in.healthconnect.widgetengine;

import in.healthconnect.widgetengine.dto.request.AiKnowledgeRequest;
import in.healthconnect.widgetengine.dto.request.AiPromptExampleRequest;
import in.healthconnect.widgetengine.repository.AiKnowledgeRepository;
import in.healthconnect.widgetengine.repository.AiPromptExampleRepository;
import in.healthconnect.widgetengine.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// Seeds the AI knowledge base into REAL MySQL (creates the two tables if missing, then
// fills them with the real HealthConnect schema + a few examples). Nothing is deleted.
//
// Run on its own with:
//   ./mvnw test -Dtest=KnowledgeBaseSeedIT
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=update")
class KnowledgeBaseSeedIT {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    @Autowired
    private AiKnowledgeRepository knowledgeRepository;
    @Autowired
    private AiPromptExampleRepository exampleRepository;

    @Test
    void seedKnowledgeBase() {
        // ---- knowledge: one row per table (skip if it already exists) ----
        addKnowledge("doctors",
                "Doctors who work at the hospital.",
                "id, doctor_code, first_name, last_name, email, phone, gender, qualification, experience_years, consultation_fee, is_deleted",
                "Always filter is_deleted = false. A doctor's specialty is found by joining to specialties through doctor_specialties_map.");

        addKnowledge("specialties",
                "Medical specialties, e.g. Cardiology, Neurology.",
                "id, name, description, is_deleted",
                "The specialty name is in specialties.name (e.g. 'Cardiology'). Filter is_deleted = false.");

        addKnowledge("doctor_specialties_map",
                "Link table connecting doctors and specialties (many-to-many).",
                "id, doctor_id, specialty_id",
                "Join doctors.id = doctor_specialties_map.doctor_id and specialties.id = doctor_specialties_map.specialty_id.");

        addKnowledge("patient",
                "Patients registered at the hospital.",
                "id, patient_code, first_name, last_name, date_of_birth, gender, phone, email, address, blood_group, is_deleted",
                "Filter is_deleted = false. Table name is singular: patient.");

        addKnowledge("appointments",
                "Appointments between a patient and a doctor.",
                "id, patient_id, doctor_id, appointment_date, start_time, end_time, duration_minutes, status, is_deleted",
                "Join appointments.doctor_id = doctors.id and appointments.patient_id = patient.id. status is an enum. Filter is_deleted = false.");

        // ---- examples: question -> correct SQL (only seed once) ----
        if (exampleRepository.findByEnabledTrue().isEmpty()) {
            addExample("count all doctors",
                    "SELECT count(*) AS `Total Doctors` FROM doctors WHERE is_deleted = false");

            addExample("list all cardiology doctors",
                    "SELECT d.first_name AS `First Name`, d.last_name AS `Last Name`, d.email AS `Email` " +
                    "FROM doctors d " +
                    "JOIN doctor_specialties_map m ON m.doctor_id = d.id " +
                    "JOIN specialties s ON s.id = m.specialty_id " +
                    "WHERE d.is_deleted = false AND s.name = 'Cardiology'");

            addExample("list all patients",
                    "SELECT first_name AS `First Name`, last_name AS `Last Name`, email AS `Email` " +
                    "FROM patient WHERE is_deleted = false");
        }

        System.out.println(">>> ai_knowledge rows: " + knowledgeRepository.findAll().size());
        System.out.println(">>> ai_prompt_example rows: " + exampleRepository.findAll().size());
    }

    private void addKnowledge(String table, String purpose, String columns, String hints) {
        if (knowledgeRepository.existsByTableName(table)) {
            return; // leave existing knowledge as is
        }
        AiKnowledgeRequest request = new AiKnowledgeRequest();
        request.setTableName(table);
        request.setPurpose(purpose);
        request.setColumnsInfo(columns);
        request.setHints(hints);
        knowledgeBaseService.createKnowledge(request);
        System.out.println(">>> added knowledge for table: " + table);
    }

    private void addExample(String question, String sql) {
        AiPromptExampleRequest request = new AiPromptExampleRequest();
        request.setQuestion(question);
        request.setGeneratedSql(sql);
        knowledgeBaseService.createExample(request);
        System.out.println(">>> added example: " + question);
    }
}

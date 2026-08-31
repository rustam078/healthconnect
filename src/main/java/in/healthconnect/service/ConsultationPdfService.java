package in.healthconnect.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import in.healthconnect.entity.Appointment;
import in.healthconnect.entity.Consultation;
import in.healthconnect.entity.Patient;
import in.healthconnect.entity.PrescriptionMedicine;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.ConsultationRepository;
import in.healthconnect.setting.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Renders a consultation to a PDF from the HTML template stored in app_setting.
//
// The template is plain-text with {{placeholders}}: scalar values are substituted directly
// and {{prescriptionRows}} is replaced with one <tr> per medicine. Every injected value is
// HTML-escaped, so a stray '<' in a complaint or a medicine name cannot break the markup.
@Service
@RequiredArgsConstructor
public class ConsultationPdfService {

    // The app_setting key holding the HTML template, and the clinic-name key.
    private static final String TEMPLATE_SETTING = "consultation.pdf-template";
    private static final String CLINIC_NAME_SETTING = "clinic.name";
    private static final String DEFAULT_CLINIC_NAME = "HealthConnect";

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ConsultationRepository consultationRepository;
    private final SettingService settingService;

    // The consultation for one appointment, rendered to PDF bytes.
    @Transactional(readOnly = true)
    public byte[] renderByAppointmentId(Integer appointmentId) {

        Consultation consultation = consultationRepository.findByAppointmentId(appointmentId).orElseThrow(() ->
                new ResourceNotFoundException(
                        "No consultation recorded for appointment id '" + appointmentId + "'"));

        String html = buildHtml(consultation);
        return renderPdf(html);
    }

    private String buildHtml(Consultation consultation) {

        Appointment appointment = consultation.getAppointment();
        Patient patient = appointment.getPatient();

        String clinicName = settingService.getOrDefault(CLINIC_NAME_SETTING, DEFAULT_CLINIC_NAME);

        String template = settingService.getRequired(TEMPLATE_SETTING);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("{{clinicName}}", esc(clinicName));
        values.put("{{documentDate}}", LocalDate.now(IST).format(DATE_FMT));
        values.put("{{patientName}}", esc(fullName(patient.getFirstName(), patient.getLastName())));
        values.put("{{patientCode}}", esc(patient.getPatientCode()));
        values.put("{{patientAgeGender}}", esc(ageGender(patient)));
        values.put("{{doctorName}}", esc(fullName(appointment.getDoctor().getFirstName(),
                appointment.getDoctor().getLastName())));
        values.put("{{appointmentDate}}", appointment.getAppointmentDate() == null ? "-"
                : appointment.getAppointmentDate().format(DATE_FMT));
        values.put("{{appointmentTime}}", timeRange(appointment));
        values.put("{{chiefComplaint}}", dash(esc(consultation.getChiefComplaint())));
        values.put("{{diagnosis}}", dash(esc(consultation.getDiagnosis())));
        values.put("{{notes}}", dash(esc(consultation.getNotes())));

        String html = template;
        for (Map.Entry<String, String> e : values.entrySet()) {
            html = html.replace(e.getKey(), e.getValue());
        }
        // Rows go in last: their escaped cell values never contain a placeholder token.
        return html.replace("{{prescriptionRows}}", prescriptionRows(consultation.getMedicines()));
    }

    private String prescriptionRows(List<PrescriptionMedicine> medicines) {
        if (medicines == null || medicines.isEmpty()) {
            return "<tr><td class=\"empty\" colspan=\"5\">No medicines were prescribed.</td></tr>";
        }
        StringBuilder rows = new StringBuilder();
        for (PrescriptionMedicine m : medicines) {
            rows.append("<tr>")
                    .append("<td>").append(dash(esc(m.getMedicineName()))).append("</td>")
                    .append("<td>").append(dash(esc(m.getDosage()))).append("</td>")
                    .append("<td>").append(dash(esc(m.getFrequency()))).append("</td>")
                    .append("<td>").append(dash(esc(m.getDuration()))).append("</td>")
                    .append("<td>").append(dash(esc(m.getInstructions()))).append("</td>")
                    .append("</tr>");
        }
        return rows.toString();
    }

    private byte[] renderPdf(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render consultation PDF", e);
        }
    }

    private String ageGender(Patient patient) {
        String gender = patient.getGender() == null ? "" : titleCase(patient.getGender().name());
        if (patient.getDateOfBirth() == null) {
            return gender.isBlank() ? "-" : gender;
        }
        int age = Period.between(patient.getDateOfBirth(), LocalDate.now(IST)).getYears();
        return gender.isBlank() ? age + " yrs" : age + " yrs / " + gender;
    }

    private String timeRange(Appointment appointment) {
        if (appointment.getStartTime() == null || appointment.getEndTime() == null) {
            return "-";
        }
        return appointment.getStartTime().format(TIME_FMT) + " - " + appointment.getEndTime().format(TIME_FMT);
    }

    private static String fullName(String firstName, String lastName) {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) return "";
        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    // Blank -> an em dash, so an empty cell reads as "nothing here" rather than a gap.
    private static String dash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    // HTML-escape so injected text is data, never markup.
    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

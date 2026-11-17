package in.healthconnect.setting;

import in.healthconnect.setting.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

// Seeds the settings that server-side features need on first run: the consultation PDF
// template (from the bundled HTML file) and the clinic name. Existing values are left alone.
@Component
@Order(3)
@RequiredArgsConstructor
public class SettingSeeder implements CommandLineRunner {

    private static final String TEMPLATE_KEY = "consultation.pdf-template";
    private static final String CLINIC_NAME_KEY = "clinic.name";

    private final SettingService settingService;

    @Override
    public void run(String... args) throws Exception {
        String template = new String(
                new ClassPathResource("templates/consultation-template.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        // Upgrade an older stored template (one without the theme placeholders) to this one.
        settingService.seedOrUpgrade(TEMPLATE_KEY, "{{primaryColor}}", template,
                "HTML template used to render the consultation/prescription PDF.");
        settingService.putIfAbsent(CLINIC_NAME_KEY, "HealthConnect",
                "Clinic name shown on the prescription PDF.");

        // PDF theme controls - edit these on the Settings screen to restyle the PDF.
        settingService.putIfAbsent("consultation.pdf.primary-color", "#0b7285",
                "PDF theme colour (header, section titles, table headers). e.g. #0b7285");
        settingService.putIfAbsent("consultation.pdf.header-padding-top", "0px",
                "Space above the PDF header. e.g. 0px, 10px, 8mm");
        settingService.putIfAbsent("consultation.pdf.header-align", "left",
                "PDF header text alignment: left, center or right");
        settingService.putIfAbsent("consultation.pdf.body-padding", "0 10mm",
                "PDF body (article) padding. e.g. '0 10mm' (top/bottom  left/right)");
    }
}

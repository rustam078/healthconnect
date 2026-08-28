package in.healthconnect.widgetengine;

import in.healthconnect.widgetengine.dto.request.CreateWidgetRequest;
import in.healthconnect.widgetengine.dto.request.ExecuteWidgetRequest;
import in.healthconnect.widgetengine.dto.response.WidgetDataResponse;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import in.healthconnect.widgetengine.entity.enums.WidgetType;
import in.healthconnect.widgetengine.repository.WidgetRepository;
import in.healthconnect.widgetengine.service.WidgetExecutionService;
import in.healthconnect.widgetengine.service.WidgetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A REAL end-to-end run against MySQL (NOT a mock, NOT H2).
//
// What it does (and it does NOT delete anything, so you can test by hand afterwards):
//   1. Creates the widget tables if they are missing (ddl-auto=update, just for this run).
//   2. Inserts a few real "specialties" rows (skips ones that already exist).
//   3. Creates a sample widget called "specialty-list" (only if it is not there yet).
//   4. Runs that widget through the whole engine and prints the rows it got back.
//
// This test is named *IT (integration test). It needs your MySQL running.
// Run it on its own with:
//   ./mvnw test -Dtest=WidgetEngineSeedIT
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=update")
class WidgetEngineSeedIT {

    @Autowired
    private WidgetService widgetService;
    @Autowired
    private WidgetExecutionService executionService;
    @Autowired
    private WidgetRepository widgetRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void seedRealDataAndRunWidget() {
        // ---- 1. Insert some real specialty rows (INSERT IGNORE skips duplicates by name) ----
        insertSpecialty("Cardiology", "Heart and blood vessels");
        insertSpecialty("Neurology", "Brain and nervous system");
        insertSpecialty("Dermatology", "Skin, hair and nails");
        insertSpecialty("Orthopedics", "Bones and joints");
        insertSpecialty("Pediatrics", "Care for children");

        // ---- 2. Create the sample widget once (skip if it already exists) ----
        if (!widgetRepository.existsByCode("specialty-list")) {
            CreateWidgetRequest request = new CreateWidgetRequest();
            request.setCode("specialty-list");
            request.setName("Specialty List");
            request.setDescription("Lists medical specialties; optional name filter.");
            request.setModule(WidgetModule.WIDGET);
            request.setType(WidgetType.TABLE);
            // :name = value (bound safely), {{name}} = operator (from our safe list)
            request.setSqlTemplate(
                    "SELECT name AS `Specialty`, description AS `Details` " +
                    "FROM specialties " +
                    "WHERE is_deleted = false " +
                    "AND name {{name}} coalesce(:name, name)");
            request.setFilters(objectMapper.readTree(
                    "{ \"filters\": [ { \"key\":\"name\", \"operators\":[\"eq\",\"like\"] } ], " +
                    "\"sortableColumns\": [\"Specialty\"] }"));
            widgetService.create(request);
            System.out.println(">>> Created sample widget 'specialty-list'.");
        } else {
            System.out.println(">>> Sample widget 'specialty-list' already exists - leaving it as is.");
        }

        // ---- 3. Run the widget through the whole engine, against real MySQL ----
        ExecuteWidgetRequest run = new ExecuteWidgetRequest();
        run.setSortBy("Specialty");
        run.setSortOrder("asc");
        run.setPageNo(1);
        run.setPageSize(10);

        WidgetDataResponse data = executionService.execute("specialty-list", run, true);

        System.out.println(">>> Widget 'specialty-list' returned " + data.getRowCount() + " row(s):");
        data.getRows().forEach(row -> System.out.println("    " + row));

        assertFalse(data.getRows().isEmpty(), "Expected at least one specialty row");
        assertTrue(data.getRows().get(0).containsKey("Specialty"));
    }

    private void insertSpecialty(String name, String description) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO specialties (name, description, is_deleted, created_at, updated_at) " +
                "VALUES (?, ?, 0, NOW(6), NOW(6))",
                name, description);
    }
}

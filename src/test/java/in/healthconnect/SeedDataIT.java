package in.healthconnect;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Proves db/data.sql loads into MySQL without a single failing statement.
//
// It runs against a THROWAWAY database - healthconnect_seedtest - created on the fly by
// the connector's createDatabaseIfNotExist flag, with ddl-auto=create building the schema.
// Your real `healthconnect` database is never touched, which matters because data.sql
// begins by DELETEing every table it seeds.
//
// Run on its own with:
//   ./mvnw test -Dtest=SeedDataIT
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/healthconnect_seedtest?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.show-sql=false",
})
class SeedDataIT {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seedFileLoadsAndProducesTheExpectedCounts() throws Exception {
        FileSystemResource script = new FileSystemResource("db/data.sql");
        assertTrue(script.exists(), "db/data.sql is missing");

        // Any failing statement throws here, which is the whole point of this test.
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, script);
        }

        assertEquals(20, count("specialties"));
        assertEquals(200, count("doctors"));
        assertEquals(5000, count("patient"));
        assertEquals(10000, count("appointments"));
        assertEquals(6, count("ai_knowledge"));
        assertEquals(7, count("ai_prompt_example"));
        // 3: nim.model, nim.base-url, and a PLACEHOLDER nim.api-key row to fill in.
        assertEquals(3, count("app_setting"));
        assertEquals(20, count("widget"));
        assertEquals(5, count("board"));
        assertTrue(count("doctor_availabilities") > 1000, "expected 1000+ availability rows");
        assertTrue(count("doctor_specialties_map") >= 200, "every doctor needs a specialty");

        // Loading twice must also work - the file deletes before it inserts.
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, script);
        }
        assertEquals(10000, count("appointments"), "re-running the seed must not duplicate rows");

        printSummary();
    }

    @Test
    void everyDoctorHasAHolidayAndNoShiftIsLongerThanTenHours() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("db/data.sql"));
        }

        // "max 10 hour including break" - the span from start to end, break included.
        Integer tooLong = jdbc.queryForObject(
                "SELECT COUNT(*) FROM doctor_availabilities "
                        + "WHERE TIMESTAMPDIFF(MINUTE, start_time, end_time) > 600", Integer.class);
        assertEquals(0, tooLong, "a shift ran longer than 10 hours");

        // Nobody works all seven days.
        Integer noHoliday = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT doctor_id FROM doctor_availabilities "
                        + "GROUP BY doctor_id HAVING COUNT(*) > 6) x", Integer.class);
        assertEquals(0, noHoliday, "every doctor must have at least one day off");

        // Some days are 5 hours with no break at all, as asked for.
        Integer shortNoBreak = jdbc.queryForObject(
                "SELECT COUNT(*) FROM doctor_availabilities "
                        + "WHERE break_start_time IS NULL AND break_end_time IS NULL "
                        + "AND TIMESTAMPDIFF(MINUTE, start_time, end_time) = 300", Integer.class);
        assertTrue(shortNoBreak > 0, "expected some 5-hour no-break days");

        // Appointments must sit inside the doctor's hours for that weekday.
        Integer outsideHours = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments a "
                        + "JOIN doctor_availabilities av ON av.doctor_id = a.doctor_id "
                        + "  AND av.day_of_week = UPPER(DAYNAME(a.appointment_date)) "
                        + "WHERE a.start_time < av.start_time OR a.end_time > av.end_time", Integer.class);
        assertEquals(0, outsideHours, "an appointment fell outside the doctor's working hours");

        // ...and every appointment lands on a day its doctor actually works.
        Integer onDayOff = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments a WHERE NOT EXISTS ("
                        + " SELECT 1 FROM doctor_availabilities av WHERE av.doctor_id = a.doctor_id"
                        + " AND av.day_of_week = UPPER(DAYNAME(a.appointment_date)))", Integer.class);
        assertEquals(0, onDayOff, "an appointment was booked on the doctor's day off");
    }

    @Test
    void everyStoredWidgetQueryActuallyRuns() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("db/data.sql"));
        }

        // A seeded widget whose SQL does not run would look broken the moment the board
        // opens, so check every one of them against the seeded data.
        for (var row : jdbc.queryForList("SELECT code, sql_template FROM widget ORDER BY id")) {
            String code = (String) row.get("code");
            String sql = (String) row.get("sql_template");
            // Neutralise the filter placeholders the engine would normally fill in.
            String runnable = sql.replaceAll("\\{\\{[A-Za-z0-9_]+}}", "=")
                    .replaceAll(":[A-Za-z0-9_]+", "NULL");
            // Only cap it when the query does not already cap itself - appending LIMIT to a
            // query that ends in "LIMIT 5" is a syntax error, the same trap SqlTemplateEngine
            // had to learn about.
            boolean selfLimiting = runnable.toLowerCase().matches("(?s).*\\blimit\\s+\\d+\\s*;?\\s*");
            try {
                jdbc.queryForList(selfLimiting ? runnable : runnable + " LIMIT 5");
            } catch (Exception e) {
                throw new AssertionError("widget '" + code + "' does not run: " + e.getMessage(), e);
            }
        }
    }


    @Test
    void everySeededAiExampleQueryActuallyRuns() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("db/data.sql"));
        }

        // These are reused VERBATIM: SqlDraftService returns an example's SQL directly when
        // a question matches it exactly, skipping the AI. A broken one would ship straight
        // to a board, so each must run against the seeded data.
        for (var row : jdbc.queryForList("SELECT question, generated_sql FROM ai_prompt_example ORDER BY id")) {
            String question = (String) row.get("question");
            String sql = (String) row.get("generated_sql");
            try {
                jdbc.queryForList(sql + " LIMIT 5");
            } catch (Exception e) {
                throw new AssertionError("example '" + question + "' does not run: " + e.getMessage(), e);
            }
        }
    }

    @Test
    void theCurrentWeekTopUpAddsRealisticAppointmentsRelativeToToday() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("db/data.sql"));
        }
        int before = count("appointments");
        java.sql.Timestamp startedAt = jdbc.queryForObject("SELECT NOW(6)", java.sql.Timestamp.class);

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("db/appointments-next-7-days.sql"));
        }
        assertEquals(before + 500, count("appointments"), "expected exactly 500 new appointments");

        // Everything it added sits in today .. today + 7 days.
        Integer outsideWindow = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE DATE(created_at) = CURDATE() "
                        + "AND (appointment_date < CURDATE() OR appointment_date > CURDATE() + INTERVAL 7 DAY)",
                Integer.class);
        assertEquals(0, outsideWindow, "an appointment landed outside the 8-day window");

        // Same guarantees the bulk seed makes: real working day, inside hours, never in the break.
        Integer onDayOff = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments a WHERE DATE(a.created_at) = CURDATE() AND NOT EXISTS ("
                        + " SELECT 1 FROM doctor_availabilities av WHERE av.doctor_id = a.doctor_id"
                        + " AND av.day_of_week = UPPER(DAYNAME(a.appointment_date)))", Integer.class);
        assertEquals(0, onDayOff, "booked on a doctor's day off");

        Integer outsideHours = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments a "
                        + "JOIN doctor_availabilities av ON av.doctor_id = a.doctor_id "
                        + "  AND av.day_of_week = UPPER(DAYNAME(a.appointment_date)) "
                        + "WHERE DATE(a.created_at) = CURDATE() "
                        + "  AND (a.start_time < av.start_time OR a.end_time > av.end_time)", Integer.class);
        assertEquals(0, outsideHours, "booked outside the doctor's hours");

        Integer inBreak = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments a "
                        + "JOIN doctor_availabilities av ON av.doctor_id = a.doctor_id "
                        + "  AND av.day_of_week = UPPER(DAYNAME(a.appointment_date)) "
                        + "WHERE DATE(a.created_at) = CURDATE() AND av.break_start_time IS NOT NULL "
                        + "  AND a.start_time < av.break_end_time AND a.end_time > av.break_start_time",
                Integer.class);
        assertEquals(0, inBreak, "booked over the doctor's break");

        Integer doubleBooked = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT doctor_id, appointment_date, start_time FROM appointments "
                        + "WHERE DATE(created_at) = CURDATE() "
                        + "GROUP BY doctor_id, appointment_date, start_time HAVING COUNT(*) > 1) x",
                Integer.class);
        assertEquals(0, doubleBooked, "a doctor was double-booked");

        // created_at / updated_at must be stamped at RUN time, not baked into the file.
        // Scoped to rows created since `startedAt`, because data.sql also writes
        // future-dated appointments whose created_at is legitimately not "now".
        Integer freshlyStamped = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE created_at >= ?", Integer.class, startedAt);
        assertEquals(500, freshlyStamped, "created_at was not stamped at execution time");

        // Running it twice just adds another 500 - the file deletes nothing.
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("db/appointments-next-7-days.sql"));
        }
        assertEquals(before + 1000, count("appointments"), "re-running should add another 500");

        System.out.println(">>> current-week top-up, appointments per day:");
        jdbc.queryForList("SELECT appointment_date AS d, COUNT(*) AS c FROM appointments "
                        + "WHERE DATE(created_at) = CURDATE() GROUP BY appointment_date ORDER BY appointment_date")
                .forEach(r -> System.out.printf(">>>   %s  %s%n", r.get("d"), r.get("c")));
        System.out.println(">>> status split:");
        jdbc.queryForList("SELECT status, COUNT(*) AS c FROM appointments "
                        + "WHERE DATE(created_at) = CURDATE() GROUP BY status")
                .forEach(r -> System.out.printf(">>>   %-10s %s%n", r.get("status"), r.get("c")));
    }
    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class);
        return n == null ? 0 : n;
    }

    private void printSummary() {
        System.out.println(">>> seed loaded into healthconnect_seedtest");
        for (String t : new String[]{"specialties", "doctors", "doctor_specialties_map", "patient",
                "doctor_availabilities", "appointments", "ai_knowledge", "ai_prompt_example",
                "app_setting", "widget", "board"}) {
            System.out.printf(">>>   %-24s %d%n", t, count(t));
        }
        System.out.println(">>> appointments by year:");
        jdbc.queryForList("SELECT YEAR(appointment_date) AS y, COUNT(*) AS c "
                        + "FROM appointments GROUP BY YEAR(appointment_date) ORDER BY y")
                .forEach(r -> System.out.printf(">>>   %s  %s%n", r.get("y"), r.get("c")));
        System.out.println(">>> appointment status:");
        jdbc.queryForList("SELECT status, COUNT(*) AS c FROM appointments GROUP BY status")
                .forEach(r -> System.out.printf(">>>   %-10s %s%n", r.get("status"), r.get("c")));
    }
}

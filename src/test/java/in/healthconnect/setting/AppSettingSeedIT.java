package in.healthconnect.setting;

import in.healthconnect.setting.repository.AppSettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// Creates the app_setting table in REAL MySQL.
//
// The app normally runs with ddl-auto=validate, which does NOT create tables. This test
// overrides it to "update" for this one run so Hibernate builds app_setting with exactly
// the schema it expects. application.properties is not changed.
//
// It deliberately inserts NOTHING: the API key is added by hand and must never be committed.
//
// Run on its own with:
//   ./mvnw test -Dtest=AppSettingSeedIT
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=update")
class AppSettingSeedIT {

    @Autowired
    private AppSettingRepository repository;

    @Test
    void createsAppSettingTable() {
        // Reaching this line at all means the context started and the table validates.
        System.out.println(">>> app_setting rows: " + repository.count());
    }
}

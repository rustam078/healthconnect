package in.healthconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class HealthConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthConnectApplication.class, args);
    }

}

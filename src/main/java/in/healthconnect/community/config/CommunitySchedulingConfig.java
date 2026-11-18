package in.healthconnect.community.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Turns on @Scheduled so the chat stream heartbeat can run.
@Configuration
@EnableScheduling
public class CommunitySchedulingConfig {
}

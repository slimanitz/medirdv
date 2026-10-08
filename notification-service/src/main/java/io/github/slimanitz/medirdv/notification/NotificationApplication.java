package io.github.slimanitz.medirdv.notification;

import io.github.slimanitz.medirdv.platform.kafka.MessagingConfiguration;
import io.github.slimanitz.medirdv.platform.security.ResourceServerSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@Import({MessagingConfiguration.class, ResourceServerSecurityConfiguration.class})
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

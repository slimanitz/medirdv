package io.github.slimanitz.medirdv.patient;

import io.github.slimanitz.medirdv.platform.kafka.KafkaErrorHandlingConfiguration;
import io.github.slimanitz.medirdv.platform.kafka.MessagingConfiguration;
import io.github.slimanitz.medirdv.platform.outbox.OutboxConfiguration;
import io.github.slimanitz.medirdv.platform.security.ResourceServerSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;

@SpringBootApplication
@Import({MessagingConfiguration.class, KafkaErrorHandlingConfiguration.class, OutboxConfiguration.class,
        ResourceServerSecurityConfiguration.class})
public class PatientApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

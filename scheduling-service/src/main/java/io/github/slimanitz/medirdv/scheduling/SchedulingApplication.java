package io.github.slimanitz.medirdv.scheduling;

import io.github.slimanitz.medirdv.platform.kafka.KafkaErrorHandlingConfiguration;
import io.github.slimanitz.medirdv.platform.kafka.MessagingConfiguration;
import io.github.slimanitz.medirdv.platform.outbox.OutboxConfiguration;
import io.github.slimanitz.medirdv.platform.security.ResourceServerSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@Import({MessagingConfiguration.class, KafkaErrorHandlingConfiguration.class, OutboxConfiguration.class,
        ResourceServerSecurityConfiguration.class})
public class SchedulingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulingApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

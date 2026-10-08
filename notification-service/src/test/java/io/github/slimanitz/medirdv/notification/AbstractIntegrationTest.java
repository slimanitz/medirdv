package io.github.slimanitz.medirdv.notification;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/** Singleton Postgres + Kafka (KRaft) containers shared by every integration test of the module. */
@SpringBootTest(properties = {
        "medirdv.outbox.poll-interval-ms=100"
})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1")
            // Recent Docker engines no longer report the container hostname, which Testcontainers
            // uses for the inter-broker listener: set it explicitly.
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName("kafka"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }
}

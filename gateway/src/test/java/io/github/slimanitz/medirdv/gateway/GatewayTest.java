package io.github.slimanitz.medirdv.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/** Routing, path rewriting and security, against a stub downstream service. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@AutoConfigureWebTestClient
class GatewayTest {

    private static HttpServer downstream;
    private static final AtomicReference<String> LAST_PATH = new AtomicReference<>();

    @BeforeAll
    static void startDownstream() throws IOException {
        downstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        downstream.createContext("/", exchange -> {
            LAST_PATH.set(exchange.getRequestURI().toString());
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        downstream.start();
    }

    @AfterAll
    static void stopDownstream() {
        downstream.stop(0);
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + downstream.getAddress().getPort();
        registry.add("PATIENT_SERVICE_URL", () -> url);
        registry.add("SCHEDULING_SERVICE_URL", () -> url);
        registry.add("NOTIFICATION_SERVICE_URL", () -> url);
    }

    @Autowired
    WebTestClient client;
    @Autowired
    RouteLocator routes;
    @LocalServerPort
    int port;
    @LocalManagementPort
    int managementPort;

    @Test
    void declaresOneRoutePerService() {
        assertThat(routes.getRoutes().map(r -> r.getId()).collectList().block())
                .containsExactlyInAnyOrder("patient-service", "scheduling-service", "notification-service");
    }

    @Test
    void rejectsAnonymousApiCalls() {
        client.get().uri("/api/appointments").exchange().expectStatus().isUnauthorized();
        client.get().uri("/somewhere-else").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void forwardsAuthenticatedCallsWithoutTheApiPrefix() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_patient")))
                .get().uri("/api/practitioners/42/slots?from=2026-10-12&to=2026-10-12")
                .exchange().expectStatus().isOk();

        assertThat(LAST_PATH.get()).isEqualTo("/practitioners/42/slots?from=2026-10-12&to=2026-10-12");
    }

    @Test
    void healthIsPublicAndCorsPreflightIsAllowedForTheWebApp() throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> health = http.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + managementPort + "/actuator/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("UP");
        }
        // Actuator is not served on the public port.
        client.get().uri("/actuator/health").exchange().expectStatus().value(s -> assertThat(s).isIn(401, 404));

        HttpRequest preflight = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/patients"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header(HttpHeaders.ORIGIN, "http://localhost:4202")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .build();
        HttpResponse<Void> response;
        try (HttpClient http = HttpClient.newHttpClient()) {
            response = http.send(preflight, HttpResponse.BodyHandlers.discarding());
        }

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .contains("http://localhost:4202");
    }
}

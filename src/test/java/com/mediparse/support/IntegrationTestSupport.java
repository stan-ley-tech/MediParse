package com.mediparse.support;

import com.mediparse.auth.AuthResponse;
import com.mediparse.auth.LoginRequest;
import com.mediparse.user.Role;
import com.mediparse.user.User;
import com.mediparse.user.UserRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Shared Testcontainers setup for tests that need the real stack rather than
 * mocks. Containers are held in static fields so every subclass in the same
 * JVM reuses the same three containers instead of paying startup cost per
 * test class.
 */
@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("mediparse")
            .withUsername("mediparse")
            .withPassword("mediparse");

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    // A plain GenericContainer with the security plugin disabled, rather than the
    // opensearch-testcontainers module's own wrapper — this keeps test setup on
    // plain HTTP instead of fighting the bundled self-signed certificate, which
    // the Docker Compose stack (see docs/security.md) accepts as a dev-only tradeoff.
    @Container
    static final GenericContainer<?> OPENSEARCH =
            new GenericContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.15.0"))
                    .withExposedPorts(9200)
                    .withEnv("discovery.type", "single-node")
                    .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                    .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(3)));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");

        registry.add("mediparse.opensearch.host", OPENSEARCH::getHost);
        registry.add("mediparse.opensearch.port", () -> OPENSEARCH.getMappedPort(9200));
        registry.add("mediparse.opensearch.scheme", () -> "http");
        registry.add("mediparse.opensearch.username", () -> "");
        registry.add("mediparse.opensearch.password", () -> "");
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** Creates a user directly (bypassing the registration endpoint, which only ever grants STAFF) and logs in for a real JWT. */
    protected String loginAs(String email, Role role) {
        String rawPassword = "Password123!";
        userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(new User(email, passwordEncoder.encode(rawPassword), "Test " + role, role)));

        AuthResponse response = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest(email, rawPassword), AuthResponse.class);
        return response.accessToken();
    }
}

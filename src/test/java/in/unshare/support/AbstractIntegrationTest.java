package in.unshare.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // Singleton container pattern: started once for the whole JVM and never stopped by a test
    // lifecycle callback, so every test class (each with its own DataJpaTest/SpringBootTest context)
    // reuses the same running Postgres instance instead of racing a per-class stop/restart.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("unshare_test")
            .withUsername("unshare")
            .withPassword("unshare");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}

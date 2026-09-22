package ru.repairradar.address;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import ru.repairradar.dto.AddressRow;
import ru.repairradar.gar.GarFixture;
import ru.repairradar.mapper.AddressMapper;
import ru.repairradar.repository.AddressRepository;
import ru.repairradar.service.AddressStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class PostgresIntegrationSupport {
    static final EmbeddedPostgres POSTGRES;
    static final Path DIRECTORY;
    static {
        try {
            DIRECTORY = Files.createTempDirectory("repairradar-test-");
            GarFixture.write(DIRECTORY, 2500);
            POSTGRES = EmbeddedPostgres.start(); // Shared for this JVM; no Docker required.
            applyInitScripts();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    POSTGRES.close();
                } catch (Exception ignored) {
                    // Остановка встроенного PostgreSQL не критична при завершении тестов.
                }
            }));
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private static void applyInitScripts() throws Exception {
        try (Connection connection = POSTGRES.getPostgresDatabase().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("001-addresses.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("004-repair-import.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("005-program-import.sql"));
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("repairradar.gar.directory", DIRECTORY::toString);
    }
    @Autowired AddressStore store;
    @Autowired AddressRepository repository;
    @Autowired AddressMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactionTemplate;
    @LocalServerPort int port;

    @BeforeEach void reset() throws Exception {
        store.clear();
        GarFixture.write(DIRECTORY, 2500);
    }
    static List<AddressRow> rows(int count, long start) {
        var rows = new ArrayList<AddressRow>();
        for (long n = start; n < start + count; n++) {
            rows.add(new AddressRow("Москва",
                    "г. Москва, ул. Тестовая, д. " + n, "ул. Тестовая", String.valueOf(n),
                    null, null, null, null, 2, 2, new UUID(0, 2), n, new UUID(0, n)));
        }
        return rows;
    }
}
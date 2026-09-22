package ru.repairradar.address;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.dto.ProgramJobView;
import ru.repairradar.entity.RepairHouse;
import ru.repairradar.repository.*;
import ru.repairradar.service.*;
import ru.repairradar.utility.RepairRequestDelay;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

class ProgramImportIT extends PostgresIntegrationSupport {

    private static final HttpServer UPSTREAM = startServer();

    private static final String FIXTURE = readFixture();

    private static final String FIRST_WORK_GUID = "e48e356c-07cf-417c-a07b-b74729761748";

    private static final String SECOND_WORK_GUID = "bc38c03a-f8c5-4529-aa18-846f652d4033";

    private static volatile Function<Received, Reply> responder = request -> new Reply(200, bodyFor(request.path()));

    private static final List<Received> REQUESTS = new CopyOnWriteArrayList<>();

    @Autowired
    private ProgramImportService imports;

    @Autowired
    private ProgramJobJournal journal;

    @Autowired
    private RepairHouseRepository houses;

    @Autowired
    private ProgramHouseRepository programHouses;

    @Autowired
    private ProgramWorkRepository programWorks;

    @Autowired
    private ProgramJobRepository programJobs;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private RepairRequestDelay delay;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void upstream(DynamicPropertyRegistry registry) {
        registry.add("repairradar.program-api.url",
                () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort() + "/objects/rpkr");
    }

    @BeforeEach
    void resetProgramData() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> imports.clear());
        houses.deleteAllInBatch();
        REQUESTS.clear();
        responder = request -> new Reply(200, bodyFor(request.path()));
    }

    @AfterAll
    static void stopServer() {
        UPSTREAM.stop(0);
    }

    @Test
    void importsProgramsForUnloadedRegionalHousesAndDoesNotRepeatThem() throws Exception {
        List<UUID> seeded = seedHouses(3);

        ProgramJobView first = terminal(imports.start());
        assertThat(first.status()).isEqualTo(RepairJobStatus.SUCCESS);
        assertThat(first.successfulHouses()).isEqualTo(3);
        assertThat(first.worksSaved()).isEqualTo(6);
        assertThat(REQUESTS).hasSize(3);
        for (UUID guid : seeded) {
            assertThat(REQUESTS.stream().map(Received::path)).contains("/objects/rpkr/" + guid);
        }
        assertThat(programHouses.count()).isEqualTo(3);
        assertThat(programWorks.count()).isEqualTo(6);
        assertThat(loadedCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select count(*) from public.program_houses where payload::jsonb ? 'works'",
                Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select count(distinct payload::text) from public.program_houses", Long.class)).isEqualTo(3);

        REQUESTS.clear();
        ProgramJobView second = terminal(imports.start());
        assertThat(second.selectedHouseGuids().size()).isZero();
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void failedHouseStaysUnloadedAndIsRetriedNextRun() throws Exception {
        List<UUID> seeded = seedHouses(3);
        UUID broken = seeded.get(0);
        responder = request -> request.path().endsWith(broken.toString())
                ? new Reply(503, "вышестоящий сервис недоступен")
                : new Reply(200, bodyFor(request.path()));

        ProgramJobView first = terminal(imports.start());
        assertThat(first.status()).isEqualTo(RepairJobStatus.PARTIAL_FAILED);
        assertThat(first.successfulHouses()).isEqualTo(2);
        assertThat(first.failedHouses()).isEqualTo(1);
        assertThat(first.errorDetails()).contains("houseGuid=" + broken, "HTTP 503");
        assertThat(loadedCount()).isEqualTo(2);
        assertThat(houses.findById(broken)).hasValueSatisfying(house ->
                assertThat(house.isProgramDataLoaded()).isFalse());

        REQUESTS.clear();
        ProgramJobView second = terminal(imports.start());
        assertThat(second.selectedHouseGuids().size()).isEqualTo(1);
        assertThat(second.selectedHouseGuids().get(0).asText()).isEqualTo(broken.toString());
        assertThat(second.status()).isEqualTo(RepairJobStatus.FAILED);
        assertThat(REQUESTS).hasSize(1);
        assertThat(REQUESTS.get(0).path()).endsWith(broken.toString());
    }

    @Test
    void skipsHousesOutsideRegionalPrograms() {
        List<UUID> seeded = seedHouses(3);
        var municipal = new RepairHouse();
        municipal.setGuid(UUID.randomUUID());
        municipal.setProgramName("Муниципальная программа капитального ремонта");
        houses.save(municipal);

        ProgramJobView result = terminal(imports.start());

        assertThat(result.selectedHouseGuids().size()).isEqualTo(3);
        assertThat(REQUESTS).hasSize(3);
        for (UUID guid : seeded) {
            assertThat(REQUESTS.stream().map(Received::path)).contains("/objects/rpkr/" + guid);
        }
        assertThat(loadedCount()).isEqualTo(3);
        assertThat(houses.findById(municipal.getGuid())).hasValueSatisfying(house ->
                assertThat(house.isProgramDataLoaded()).isFalse());
    }

    @Test
    void responseWithoutWorksIsNotTreatedAsLoaded() throws Exception {
        seedHouses(1);
        responder = request -> new Reply(200, "{\"guid\":\"11111111-1111-1111-1111-111111111111\"}");

        ProgramJobView result = terminal(imports.start());

        assertThat(result.status()).isEqualTo(RepairJobStatus.FAILED);
        assertThat(result.errorDetails()).contains("no works array");
        assertThat(loadedCount()).isZero();
        assertThat(programHouses.count()).isZero();
        assertThat(programJobs.count()).isEqualTo(1);
    }

    @Test
    void concurrentOperationsAreRejectedWithConflictWhileJobRuns() throws Exception {
        seedHouses(3);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timed out");
            }
            return null;
        }).when(delay).pause();

        UUID job;
        try {
            var started = send("POST", "");
            assertThat(started.statusCode()).isEqualTo(202);
            job = UUID.fromString(json.readTree(started.body()).get("jobId").asString());
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(send("POST", "").statusCode()).isEqualTo(409);
            assertThat(send("DELETE", "").statusCode()).isEqualTo(409);
        } finally {
            release.countDown();
        }

        ProgramJobView result = terminal(job);
        assertThat(result.status()).isEqualTo(RepairJobStatus.SUCCESS);
        assertThat(loadedCount()).isEqualTo(3);
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/program-imports" + path))
                .timeout(Duration.ofSeconds(5));
        if (method.equals("DELETE")) {
            builder.DELETE();
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private ProgramJobView terminal(UUID id) {
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            RepairJobStatus status = journal.get(id).status();
            return status != RepairJobStatus.QUEUED && status != RepairJobStatus.RUNNING;
        });
        return journal.get(id);
    }

    private List<UUID> seedHouses(int count) {
        var guids = new ArrayList<UUID>();
        for (int i = 0; i < count; i++) {
            UUID guid = new UUID(0, 1000 + i);
            var house = new RepairHouse();
            house.setGuid(guid);
            house.setProgramName("Региональная программа капитального ремонта");
            houses.save(house);
            guids.add(guid);
        }
        return guids;
    }

    private long loadedCount() {
        return jdbc.queryForObject(
                "select count(*) from public.repair_houses where program_data_loaded = true", Long.class);
    }

    private static String bodyFor(String path) {
        UUID house = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
        return FIXTURE
                .replace(FIRST_WORK_GUID, new UUID(0, house.getLeastSignificantBits()).toString())
                .replace(SECOND_WORK_GUID, new UUID(0, house.getLeastSignificantBits() ^ 0x5EED5EED).toString());
    }

    private static String readFixture() {
        try (var input = ProgramImportIT.class.getResourceAsStream("/program-response.json")) {
            return new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static HttpServer startServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/objects/rpkr", ProgramImportIT::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var request = new Received(exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("User-Agent"));
            REQUESTS.add(request);
            Reply reply = responder.apply(request);
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private record Received(String method, String path, String userAgent) {
    }

    private record Reply(int status, String body) {
    }
}
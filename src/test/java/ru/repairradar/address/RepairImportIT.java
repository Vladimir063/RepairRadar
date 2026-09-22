package ru.repairradar.address;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.repairradar.dto.*;
import ru.repairradar.entity.Street;
import ru.repairradar.exception.RepairImportException;
import ru.repairradar.repository.*;
import ru.repairradar.service.*;
import ru.repairradar.utility.RepairRequestDelay;
import tools.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

class RepairImportIT extends PostgresIntegrationSupport {

    private static final HttpServer UPSTREAM = startServer();
    private static volatile Function<Received, Reply> responder = request -> new Reply(200, "{\"items\":[]}");
    private static final List<Received> REQUESTS = new CopyOnWriteArrayList<>();

    @Autowired
    private RepairImportService imports;

    @Autowired
    private RepairJobJournal journal;

    @Autowired
    private RepairDataCleaner cleaner;

    @Autowired
    private RepairPageStore pageStore;

    @Autowired
    private RepairHouseRepository houses;

    @Autowired
    private RepairKprWorkRepository kprWorks;

    @Autowired
    private RepairRegionalWorkRepository regionalWorks;

    @Autowired
    private RepairWorkGroupRepository groups;

    @Autowired
    private StreetRepository streets;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private RepairRequestDelay delay;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void upstream(DynamicPropertyRegistry registry) {
        registry.add("repairradar.repair-api.url", () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort() + "/search");
    }

    @BeforeEach
    void resetRepairData() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> imports.clear());
        streets.deleteAllInBatch();
        REQUESTS.clear();
        responder = request -> new Reply(200, "{\"items\":[]}");
    }

    @AfterAll
    static void stopServer() {
        UPSTREAM.stop(0);
    }

    @Test
    void asyncApiContinuesOtherStreetsAndTracksPartialFailure() throws Exception {
        seedStreets(3);
        String fixture = fixture();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        responder = request -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    return new Reply(500, "test timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Reply(500, "interrupted");
            }
            JsonNode body = json.readTree(request.body());
            if (body.get("fiasAddress").get("streetGuid").asString().equals(new UUID(0, 1000).toString())) {
                return new Reply(503, "upstream unavailable in test");
            }
            return new Reply(200, body.get("pageIndex").asInt() == 1 ? fixture : "{\"items\":[],\"count\":234}");
        };
        UUID job;
        try {
            var started = send("POST", "");
            assertThat(started.statusCode()).isEqualTo(202);
            job = UUID.fromString(json.readTree(started.body()).get("jobId").asString());
            assertThat(started.headers().firstValue("Location")).hasValue("/api/repair-imports/" + job);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(send("GET", "/" + job).statusCode()).isEqualTo(200);
            assertThat(journal.get(job).status()).isEqualTo(RepairJobStatus.RUNNING);
            assertThat(send("POST", "").statusCode()).isEqualTo(409);
            assertThat(send("DELETE", "").statusCode()).isEqualTo(409);
        } finally {
            release.countDown();
        }

        RepairJobView result = terminal(job);
        assertThat(result.status()).isEqualTo(RepairJobStatus.PARTIAL_FAILED);
        assertThat(result.successfulStreets()).isEqualTo(2);
        assertThat(result.failedStreets()).isEqualTo(1);
        assertThat(result.pagesSaved()).isEqualTo(2);
        assertThat(result.itemsSaved()).isEqualTo(4);
        assertThat(result.startedAt()).isNotNull();
        assertThat(result.finishedAt()).isAfterOrEqualTo(result.startedAt());
        assertThat(result.errorDetails()).contains("503", "pageIndex=1", "upstream unavailable in test");
        assertThat(houses.count()).isEqualTo(2);
        assertThat(REQUESTS).hasSize(5);
        for (Received request : REQUESTS) {
            assertThat(request.userAgent()).isEqualTo("PostmanRuntime/7.51.0");
            assertThat(request.contentType()).startsWith("application/json");
            assertThat(request.method()).isEqualTo("POST");
            var body = json.readTree(request.body());
            assertThat(body.get("itemsPerPage").asInt()).isEqualTo(100);
            assertThat(body.get("fiasAddress").get("regionGuid").asString())
                    .isEqualTo("0c5b2444-70a0-4932-980c-b4dc0d3f02b5");
        }
        verify(delay, times(5)).pause();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(send("DELETE", "").statusCode()).isEqualTo(204));
        assertThat(send("GET", "/" + job).statusCode()).isEqualTo(404);
        for (String table : List.of("repair_houses", "repair_kpr_works", "repair_regional_works", "repair_work_groups", "repair_pages", "repair_jobs")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).isZero();
        }
        assertThat(streets.count()).isEqualTo(3);
    }

    @Test
    void samplesTwentyDifferentStreetsAndReconcilesAbandonedJobs() {
        seedStreets(25);
        UUID abandoned = journal.create(List.of());
        journal.started(abandoned);

        RepairJobView result = terminal(imports.start());

        assertThat(result.status()).isEqualTo(RepairJobStatus.SUCCESS);
        assertThat(result.successfulStreets()).isEqualTo(20);
        assertThat(result.selectedStreetGuids().size()).isEqualTo(20);
        assertThat(REQUESTS).hasSize(20);
        assertThat(REQUESTS.stream().map(request -> json.readTree(request.body())
                .get("fiasAddress").get("streetGuid").asString()).distinct().count()).isEqualTo(20);
        assertThat(journal.get(abandoned).status()).isEqualTo(RepairJobStatus.INTERRUPTED);
    }

    @Test
    void marksCompletedStreetsLoadedAndSkipsThemOnNextRun() throws Exception {
        seedStreets(3);
        responder = request -> new Reply(200, "{\"items\":[]}");

        RepairJobView first = terminal(imports.start());
        assertThat(first.status()).isEqualTo(RepairJobStatus.SUCCESS);
        assertThat(first.successfulStreets()).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select count(*) from public.streets where repair_data_loaded = true", Long.class)).isEqualTo(3);
        assertThat(streets.findUnloadedStreetGuids()).isEmpty();

        REQUESTS.clear();
        RepairJobView second = terminal(imports.start());
        assertThat(second.status()).isEqualTo(RepairJobStatus.SUCCESS);
        assertThat(second.selectedStreetGuids()).isEmpty();
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void retriesOnlyFailedStreetsAndKeepsLoadedOnesSkipped() throws Exception {
        seedStreets(2);
        responder = request -> {
            if (json.readTree(request.body()).get("fiasAddress").get("streetGuid").asString()
                    .equals(new UUID(0, 1000).toString())) {
                return new Reply(503, "upstream unavailable in test");
            }
            return new Reply(200, "{\"items\":[]}");
        };

        RepairJobView first = terminal(imports.start());
        assertThat(first.status()).isEqualTo(RepairJobStatus.PARTIAL_FAILED);
        assertThat(first.successfulStreets()).isEqualTo(1);
        assertThat(first.failedStreets()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from public.streets where repair_data_loaded = true", Long.class)).isEqualTo(1);

        REQUESTS.clear();
        RepairJobView second = terminal(imports.start());
        assertThat(second.status()).isEqualTo(RepairJobStatus.FAILED);
        assertThat(second.successfulStreets()).isZero();
        assertThat(second.failedStreets()).isEqualTo(1);
        assertThat(REQUESTS).hasSize(1);
    }

    @Test
    void upsertsAllEntitiesPreservesNullsAndUnknownContracts() throws Exception {
        var response = json.readValue(fixture(), RepairResponse.class);
        UUID job = journal.create(List.of());
        UUID street = UUID.randomUUID();
        pageStore.save(job, street, 1, response, fixture());
        assertThat(houses.count()).isEqualTo(2);
        assertThat(regionalWorks.count()).isEqualTo(2);
        assertThat(kprWorks.count()).isEqualTo(2);
        long groupCount = groups.count();
        var first = response.items().getFirst();
        first.setProgramName("updated program");
        first.getKprWorks().getFirst().setContracts(json.readTree("[{\"futureField\":{\"value\":123}}]"));
        pageStore.save(job, street, 2, response, json.writeValueAsString(response));

        assertThat(houses.count()).isEqualTo(2);
        assertThat(groups.count()).isEqualTo(groupCount);
        assertThat(houses.findById(first.getGuid())).hasValueSatisfying(house -> {
            assertThat(house.getProgramName()).isEqualTo("updated program");
            assertThat(house.getProgramType().getGuid()).isNull();
            assertThat(house.getProgramType().getCode()).isEqualTo("3");
            assertThat(house.getStartDate()).isEqualTo(first.getStartDate());
        });
        assertThat(kprWorks.findById(first.getKprWorks().getFirst().getGuid())).hasValueSatisfying(work -> {
            assertThat(json.readTree(work.getContracts())).isEqualTo(first.getKprWorks().getFirst().getContracts());
            assertThat(work.getOwnerSum()).isEqualByComparingTo(first.getKprWorks().getFirst().getOwnerSum());
        });
        var minimal = new RepairResponse.House();
        minimal.setGuid(UUID.randomUUID());
        var optional = new RepairResponse(List.of(minimal), null, null);
        pageStore.save(job, street, 3, optional, json.writeValueAsString(optional));
        assertThat(houses.findById(minimal.getGuid())).hasValueSatisfying(house -> assertThat(house.getProgramType()).isNull());
    }

    @Test
    void reSavingHousesDoesNotResetProgramDataLoadedFlag() throws Exception {
        var response = json.readValue(fixture(), RepairResponse.class);
        UUID job = journal.create(List.of());
        UUID street = UUID.randomUUID();
        pageStore.save(job, street, 1, response, fixture());
        assertThat(houses.count()).isEqualTo(2);

        UUID guid = response.items().getFirst().getGuid();
        transactionTemplate.executeWithoutResult(status -> houses.markProgramLoaded(guid));
        assertThat(houses.findById(guid)).hasValueSatisfying(house ->
                assertThat(house.isProgramDataLoaded()).isTrue());

        pageStore.save(job, street, 2, response, fixture());

        assertThat(houses.findById(guid)).hasValueSatisfying(house ->
                assertThat(house.isProgramDataLoaded()).isTrue());
    }

    @Test
    void failedPageRollsBackUpdatesAndKeepsEarlierPageAndProgress() throws Exception {
        var response = json.readValue(fixture(), RepairResponse.class);
        UUID job = journal.create(List.of());
        UUID street = UUID.randomUUID();
        pageStore.save(job, street, 1, response, fixture());
        var first = response.items().getFirst();
        String originalName = first.getProgramName();
        first.setProgramName("must roll back");
        response.items().getLast().getRegionalWorks().getFirst().setGuid(null);

        assertThatThrownBy(() -> pageStore.save(job, street, 2, response, "{}"))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        assertThat(houses.findById(first.getGuid())).hasValueSatisfying(house -> assertThat(house.getProgramName()).isEqualTo(originalName));
        assertThat(journal.get(job).pagesSaved()).isEqualTo(1);
        assertThat(journal.get(job).itemsSaved()).isEqualTo(2);
    }

    @Test
    void sqlFailureRollsBackPageButErrorJournalCommitsIndependently() throws Exception {
        var response = json.readValue(fixture(), RepairResponse.class);
        UUID job = journal.create(List.of());
        UUID street = UUID.randomUUID();

        Throwable failure = catchThrowable(() -> pageStore.save(job, street, 1, response, "not-json"));

        assertThat(failure).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(houses.count()).isZero();
        assertThat(kprWorks.count()).isZero();
        assertThat(regionalWorks.count()).isZero();
        assertThat(groups.count()).isZero();
        assertThat(journal.get(job).pagesSaved()).isZero();
        journal.streetFailed(job, street, (Exception) failure);
        journal.finished(job);
        assertThat(journal.get(job).status()).isEqualTo(RepairJobStatus.FAILED);
        assertThat(journal.get(job).errorDetails()).contains("json", street.toString());
    }

    @Test
    void missingItemsIsAnErrorRatherThanASuccessfulEmptyStreet() {
        seedStreets(1);
        responder = request -> new Reply(200, "{\"count\":10}");

        RepairJobView result = terminal(imports.start());

        assertThat(result.status()).isEqualTo(RepairJobStatus.FAILED);
        assertThat(result.errorDetails()).contains("no items array", "pageIndex=1");
        assertThat(result.pagesSaved()).isZero();
    }

    private RepairJobView terminal(UUID id) {
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            RepairJobStatus status = journal.get(id).status();
            return status != RepairJobStatus.QUEUED && status != RepairJobStatus.RUNNING;
        });
        return journal.get(id);
    }

    private void seedStreets(int count) {
        for (int i = 0; i < count; i++) {
            var street = new Street();
            street.setStreetGuid(new UUID(0, 1000 + i));
            street.setStreetObjectId(1000 + i);
            street.setName("Test " + i);
            street.setCity("Moscow");
            streets.save(street);
        }
    }

    private String fixture() throws IOException {
        try (var input = getClass().getResourceAsStream("/repair-response.json")) {
            return new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private HttpResponse<String> send(String method, String suffix) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/repair-imports" + suffix))
                .timeout(Duration.ofSeconds(5)).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer startServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/search", RepairImportIT::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var request = new Received(exchange.getRequestMethod(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    exchange.getRequestHeaders().getFirst("User-Agent"),
                    exchange.getRequestHeaders().getFirst("Content-Type"));
            REQUESTS.add(request);
            Reply reply = responder.apply(request);
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private record Received(String method, String body, String userAgent, String contentType) {
    }

    private record Reply(int status, String body) {
    }
}

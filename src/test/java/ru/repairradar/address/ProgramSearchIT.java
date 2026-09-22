package ru.repairradar.address;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.repairradar.entity.ProgramHouse;
import ru.repairradar.entity.ProgramWork;
import ru.repairradar.repository.ProgramHouseRepository;
import ru.repairradar.repository.ProgramWorkRepository;
import ru.repairradar.utility.ProgramDates;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramSearchIT extends PostgresIntegrationSupport {

    @Autowired
    private ProgramHouseRepository programHouses;

    @Autowired
    private ProgramWorkRepository programWorks;

    @Autowired
    private ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clearPrograms() {
        programWorks.deleteAllInBatch();
        programHouses.deleteAllInBatch();
    }

    @Test
    void returnsUniqueSortedWorkTypeNamesWithoutEmptyValues() throws Exception {
        for (String name : new String[]{"Фасад", "Кровля", "Фасад", null, "", "   "}) {
            seedAddress("Москва", name, "01.2030", "12.2032");
        }

        var response = get("/api/programs/work-types");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()))
                .isEqualTo(json.readTree("[\"Кровля\",\"Фасад\"]"));
    }

    @Test
    void returnsEmptyWorkTypesForEmptyTable() throws Exception {
        var response = get("/api/programs/work-types");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body())).isEqualTo(json.readTree("[]"));
    }

    @Test
    void documentsBothEndpointsAndServesSwaggerUi() throws Exception {
        var response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        var paths = json.readTree(response.body()).path("paths");
        for (String path : new String[]{"/api/programs/work-types", "/api/programs/search"}) {
            var operation = paths.path(path).path("get");
            assertThat(operation.path("summary").asText()).isNotBlank();
            var schema = operation.path("responses").path("200").path("content")
                    .path("*/*").path("schema");
            assertThat(schema.path("type").asText()).isEqualTo("array");
            assertThat(schema.path("items").path("type").asText()).isEqualTo("string");
        }
        var parameters = paths.path("/api/programs/search").path("get").path("parameters");
        assertThat(parameters.size()).isEqualTo(3);
        var names = new java.util.ArrayList<String>();
        for (var parameter : parameters) {
            names.add(parameter.path("name").asText());
            assertThat(parameter.path("required").asBoolean()).isTrue();
            assertThat(parameter.path("in").asText()).isEqualTo("query");
        }
        assertThat(names).containsExactlyInAnyOrder("workTypeName", "startDate", "endDate");
        var ui = get("/swagger-ui/index.html");
        assertThat(ui.statusCode()).isEqualTo(200);
        assertThat(ui.body()).contains("Swagger UI");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .timeout(java.time.Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void findsHouseAddressesByWorkTypeAndOverlappingPeriod() throws Exception {
        seedAddress("125130, Москва г, ул. Клары Цеткин, д. 29, корп. 1",
                "ремонт или замена внутреннего водостока", "01.2030", "12.2032");
        seedAddress("125212, Москва г, ул. Кронштадтский б-р, д. 1",
                "ремонт или замена внутреннего водостока", "01.2042", "12.2044");

        var response = send("водостока", "01.2042", "12.2044");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()))
                .isEqualTo(json.readTree("[\"125212, Москва г, ул. Кронштадтский б-р, д. 1\"]"));
    }

    @Test
    void matchesNameCaseInsensitivelyAndExcludesOutOfRangePeriods() throws Exception {
        seedAddress("125130, Москва г, ул. Клары Цеткин, д. 29, корп. 1",
                "Ремонт ИЛИ замена внутреннего водостока", "01.2030", "12.2032");

        var response = send("ВОДОСТОКА", "01.2030", "12.2032");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()))
                .isEqualTo(json.readTree("[\"125130, Москва г, ул. Клары Цеткин, д. 29, корп. 1\"]"));

        var outOfRange = send("ВОДОСТОКА", "01.2042", "12.2044");
        assertThat(json.readTree(outOfRange.body())).isEqualTo(json.readTree("[]"));
    }

    @Test
    void malformedDatesReturnBadRequestWithProgramCode() throws Exception {
        var response = send("водостока", "13.2030", "12.2032");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("PROGRAM_SEARCH_INVALID");
    }

    @Test
    void missingParameterReturnsBadRequestWithProgramCode() throws Exception {
        var response = send("водостока", "01.2030", null);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("PROGRAM_SEARCH_INVALID");
    }

    private HttpResponse<String> send(String name, String start, String end) throws Exception {
        StringBuilder url = new StringBuilder("http://localhost:" + port + "/api/programs/search?");
        if (name != null) {
            url.append("workTypeName=").append(URLEncoder.encode(name, StandardCharsets.UTF_8)).append('&');
        }
        if (start != null) {
            url.append("startDate=").append(start).append('&');
        }
        if (end != null) {
            url.append("endDate=").append(end);
        }
        return http.send(HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(java.time.Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void seedAddress(String address, String workTypeName, String start, String end) {
        var house = new ProgramHouse();
        house.setHouseGuid(UUID.randomUUID());
        house.setProgramGuid(UUID.randomUUID());
        house.setHouseAddress(address);
        house.setPayload("{}");
        var work = new ProgramWork();
        work.setGuid(UUID.randomUUID());
        work.setHouse(house);
        work.setWorkNumber(1L);
        work.setCapitalRepairWorkTypeName(workTypeName);
        work.setStartDate(ProgramDates.start(start));
        work.setEndDate(ProgramDates.end(end));
        house.getWorks().add(work);
        programHouses.save(house);
    }
}

package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.repairradar.exception.OperationInProgressException;
import ru.repairradar.service.AddressImportService;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddressErrorsIT extends PostgresIntegrationSupport {
    @MockitoBean AddressImportService service;

    @Test
    void busyOperationReturns409() throws Exception {
        when(service.importAddresses()).thenThrow(new OperationInProgressException());
        var response = request();
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("ADDRESS_OPERATION_IN_PROGRESS");
    }

    @Test
    void databaseErrorReturns503WithoutInternalDetails() throws Exception {
        when(service.importAddresses()).thenThrow(new DataAccessResourceFailureException("private JDBC SQL details"));
        var response = request();
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("DATABASE_ERROR").doesNotContain("private", "JDBC", "SQL");
    }

    @Test
    void unexpectedErrorReturns500ProblemJsonWithoutInternalDetails() throws Exception {
        when(service.importAddresses()).thenThrow(new IllegalStateException("secret internal detail"));
        var response = request();
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("content-type")).hasValue("application/problem+json");
        assertThat(response.body()).contains("INTERNAL_ERROR").doesNotContain("secret", "internal");
    }

    @Test
    void unknownRouteReturns404ProblemJson() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/addresses/unknown"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("content-type")).hasValue("application/problem+json");
        assertThat(response.body()).contains("ADDRESS_RESOURCE_NOT_FOUND");
    }

    private HttpResponse<String> request() throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:"+port+"/api/addresses/import"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
}

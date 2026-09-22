package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import ru.repairradar.dto.GarFiles;
import ru.repairradar.dto.ImportResult;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import static org.assertj.core.api.Assertions.*;

class AddressApiIT extends PostgresIntegrationSupport {
    @Autowired ObjectMapper mapper;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test void importsUntilExhaustedAndDeletes() throws Exception {
        for (int expected : new int[]{1000,2000,2500,2500}) {
            long before = repository.count();
            var response = send("POST","/import");
            assertThat(response.statusCode()).isEqualTo(200);
            var result = mapper.readValue(response.body(),ImportResult.class);
            assertThat(result.requested()).isEqualTo(1000);
            assertThat(result.imported()).isEqualTo((int)(expected-before));
            assertThat(result.total()).isEqualTo(expected);
            assertThat(result.exhausted()).isEqualTo(result.imported()<1000);
            assertThat(repository.count()).isEqualTo(expected);
        }
        var deleted = send("DELETE","");
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(deleted.body()).isEmpty();
        assertThat(repository.count()).isZero();
        assertThat(send("DELETE","").statusCode()).isEqualTo(204);
        assertThat(mapper.readValue(send("POST","/import").body(),ImportResult.class).imported()).isEqualTo(1000);
    }
    @Test void invalidXmlReturns422AndPreservesOldRows() throws Exception {
        assertThat(send("POST","/import").statusCode()).isEqualTo(200);
        var before = jdbc.queryForList("select * from addresses order by house_guid");
        Files.writeString(GarFiles.discover(DIRECTORY).houses(),"<HOUSES>");
        var response = send("POST","/import");
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.headers().firstValue("content-type")).hasValue("application/problem+json");
        assertThat(mapper.readTree(response.body()).get("code").asString()).isEqualTo("GAR_XML_INVALID");
        assertThat(response.body()).doesNotContain(DIRECTORY.toString());
        assertThat(jdbc.queryForList("select * from addresses order by house_guid")).isEqualTo(before);
    }
    private HttpResponse<String> send(String method,String suffix) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/addresses"+suffix))
                .method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
}

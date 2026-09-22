package ru.repairradar.address;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import ru.repairradar.dto.StreetImportResult;
import ru.repairradar.dto.StreetRow;
import ru.repairradar.gar.GarStreetFixture;
import ru.repairradar.repository.StreetRepository;
import ru.repairradar.service.StreetStore;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreetImportIT extends PostgresIntegrationSupport {

    @Autowired
    private StreetStore streetStore;

    @Autowired
    private StreetRepository streetRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetStreets() {
        streetRepository.deleteAllInBatch();
    }

    @Test
    void importsAllStreetsInOneRequestAndRepeatedImportDoesNotDuplicate() throws Exception {
        GarStreetFixture.write(DIRECTORY, 2501);
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/streets/import"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();

        var first = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readValue(first.body(), StreetImportResult.class))
                .isEqualTo(new StreetImportResult(2501, 0, 2501));
        var second = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readValue(second.body(), StreetImportResult.class))
                .isEqualTo(new StreetImportResult(0, 0, 2501));
        assertThat(streetRepository.findById(new UUID(0, 100))).hasValueSatisfying(street -> {
            assertThat(street.getStreetObjectId()).isEqualTo(100);
            assertThat(street.getName()).isEqualTo("ул. Тестовая 0");
            assertThat(street.getCity()).isEqualTo("Москва");
            assertThat(street.getFullAddress()).isEqualTo("ул. Тестовая 0, г. Троицк, г. Москва");
            assertThat(street.getLocality()).isEqualTo("г. Троицк");
            assertThat(street.getHierarchyPath()).isEqualTo("1.2.100");
        });
        assertThat(repository.count()).isZero();
    }

    @Test
    void rollsBackEntireImportOnInvalidRowAndPreservesExistingStreets() {
        var existing = row(1, "ул. Существующая", "с. Кленово");
        streetStore.saveAll(List.of(existing));
        var changed = row(1, "ул. Изменённая", "п. ЛМС");
        var valid = row(2, "ул. Новая", null);
        var invalid = row(3, " ", null);

        assertThatThrownBy(() -> streetStore.saveAll(List.of(changed, valid, invalid)))
                .isInstanceOf(DataAccessException.class);
        assertThat(streetRepository.count()).isEqualTo(1);
        assertThat(streetRepository.findById(existing.streetGuid())).hasValueSatisfying(street -> {
            assertThat(street.getName()).isEqualTo(existing.name());
            assertThat(street.getFullAddress()).isEqualTo(existing.fullAddress());
        });
    }

    @Test
    void backfillsLegacyRowsAndKeepsSameNamedStreetsSeparate() {
        var first = row(100, "ул. Садовая", "с. Кленово");
        var second = row(101, "ул. Садовая", "п. ЛМС");
        jdbc.update("INSERT INTO streets (street_guid, street_object_id, name, city) VALUES (?, ?, ?, ?)",
                first.streetGuid(), first.streetObjectId(), first.name(), first.city());

        assertThat(streetStore.saveAll(List.of(first, second))).isEqualTo(new StreetImportResult(1, 1, 2));
        assertThat(streetRepository.findAll()).extracting(street -> street.getFullAddress())
                .containsExactlyInAnyOrder(first.fullAddress(), second.fullAddress());
        assertThat(streetStore.saveAll(List.of(first, second))).isEqualTo(new StreetImportResult(0, 0, 2));

        var moved = new StreetRow(first.streetGuid(), first.streetObjectId(), first.name(), first.city(),
                "ул. Садовая, р-н Другой, г. Москва", null, "1.42.100");
        assertThat(streetStore.saveAll(List.of(moved, second))).isEqualTo(new StreetImportResult(0, 1, 2));
        assertThat(streetRepository.findById(first.streetGuid())).hasValueSatisfying(street -> {
            assertThat(street.getFullAddress()).isEqualTo(moved.fullAddress());
            assertThat(street.getLocality()).isNull();
            assertThat(street.getHierarchyPath()).isEqualTo("1.42.100");
        });
    }

    private StreetRow row(long id, String name, String locality) {
        String fullAddress = name + (locality == null ? "" : ", " + locality) + ", г. Москва";
        return new StreetRow(new UUID(0, id), id, name, "Москва", fullAddress, locality, "1." + id);
    }
}

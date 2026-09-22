package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import ru.repairradar.config.GarProperties;
import ru.repairradar.dto.StreetImportResult;
import ru.repairradar.exception.GarImportException;
import ru.repairradar.exception.OperationInProgressException;
import ru.repairradar.service.GarStreetReader;
import ru.repairradar.service.StreetImportService;
import ru.repairradar.service.StreetStore;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StreetImportServiceTest {

    @Test
    void rejectsOverlappingImportAndReleasesGuardAfterFailure() {
        var reader = mock(GarStreetReader.class);
        var store = mock(StreetStore.class);
        Path directory = Path.of("77");
        var service = new StreetImportService(reader, store, new GarProperties(directory));
        when(reader.readCandidates(directory)).thenAnswer(invocation -> {
            assertThatThrownBy(service::importStreets).isInstanceOf(OperationInProgressException.class);
            throw new GarImportException("GAR_XML_INVALID", "Broken XML");
        }).thenReturn(List.of());
        when(store.saveAll(List.of())).thenReturn(new StreetImportResult(0, 0, 0));

        assertThatThrownBy(service::importStreets).isInstanceOf(GarImportException.class);
        assertThat(service.importStreets()).isEqualTo(new StreetImportResult(0, 0, 0));
    }
}

package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import ru.repairradar.config.GarProperties;
import ru.repairradar.exception.GarImportException;
import ru.repairradar.exception.OperationInProgressException;
import ru.repairradar.service.AddressImportService;
import ru.repairradar.service.AddressStore;
import ru.repairradar.service.GarAddressReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddressImportServiceTest {
    @Test void readFailureDoesNotWriteAndReleasesGuard() {
        var reader = mock(GarAddressReader.class);
        var store = mock(AddressStore.class);
        var service = new AddressImportService(reader, store, new GarProperties(Path.of("77")));
        when(reader.readCandidates(Path.of("77"))).thenThrow(new GarImportException("GAR_XML_INVALID", "broken"));
        assertThatThrownBy(service::importAddresses).isInstanceOf(GarImportException.class);
        verifyNoInteractions(store);
        service.clearAddresses();
        verify(store).clear();
    }
    @Test void concurrentOperationsFailFastAndGuardIsReleased() throws Exception {
        var reader = mock(GarAddressReader.class);
        var store = mock(AddressStore.class);
        var service = new AddressImportService(reader, store, new GarProperties(Path.of("77")));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(reader.readCandidates(any())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
            return List.of();
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var task = executor.submit(service::importAddresses);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(service::importAddresses).isInstanceOf(OperationInProgressException.class);
                assertThatThrownBy(service::clearAddresses).isInstanceOf(OperationInProgressException.class);
                verifyNoInteractions(store);
            } finally { release.countDown(); }
            task.get(10, TimeUnit.SECONDS);
        }
        service.clearAddresses();
        verify(store).clear();
    }
}

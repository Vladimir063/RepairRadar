package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import ru.repairradar.config.RepairApiProperties;
import ru.repairradar.dto.RepairApiPage;
import ru.repairradar.dto.RepairResponse;
import ru.repairradar.exception.RepairImportException;
import ru.repairradar.service.RepairApiClient;
import ru.repairradar.service.RepairPageStore;
import ru.repairradar.service.RepairStreetLoader;
import ru.repairradar.service.StreetStore;
import ru.repairradar.utility.RepairRequestDelay;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepairStreetLoaderTest {

    private final RepairApiClient client = mock(RepairApiClient.class);
    private final RepairPageStore store = mock(RepairPageStore.class);
    private final StreetStore streetStore = mock(StreetStore.class);
    private final RepairRequestDelay delay = mock(RepairRequestDelay.class);
    private final UUID job = UUID.randomUUID();
    private final UUID street = UUID.randomUUID();

    @Test
    void readsShortPagesUntilEmptyRegardlessOfCountAndPacesEveryRequest() throws Exception {
        var first = page(1);
        var second = page(2);
        when(client.fetch(street, 1)).thenReturn(first);
        when(client.fetch(street, 2)).thenReturn(second);
        when(client.fetch(street, 3)).thenReturn(new RepairApiPage(new RepairResponse(List.of(), null, null), "{}"));

        loader(10).load(job, street);

        var order = inOrder(delay, client, store);
        for (int i = 1; i <= 3; i++) {
            order.verify(delay).pause();
            order.verify(client).fetch(street, i);
            if (i <= 2) {
                order.verify(store).save(eq(job), eq(street), eq(i), any(), anyString());
            }
        }
        verifyNoMoreInteractions(client, store, delay);
        verify(streetStore).markRepairDataLoaded(street);
    }

    @Test
    void repeatedPageFailsInsteadOfLoopingForever() {
        when(client.fetch(eq(street), anyInt())).thenReturn(page(1));

        assertThatThrownBy(() -> loader(10).load(job, street)).isInstanceOf(RepairImportException.class)
                .hasMessageContaining("pageIndex=2").hasMessageContaining("repeated");
        verify(store, times(1)).save(eq(job), eq(street), eq(1), any(), anyString());
        verifyNoInteractions(streetStore);
    }

    @Test
    void pageLimitReportsIncompleteImport() {
        when(client.fetch(street, 1)).thenReturn(page(1));
        when(client.fetch(street, 2)).thenReturn(page(2));

        assertThatThrownBy(() -> loader(2).load(job, street)).isInstanceOf(RepairImportException.class)
                .hasMessageContaining("import incomplete");
        verify(store, times(2)).save(eq(job), eq(street), anyInt(), any(), anyString());
        verifyNoInteractions(streetStore);
    }

    @Test
    void interruptedDelayDoesNotSendRequest() throws Exception {
        doThrow(new InterruptedException("stopped")).when(delay).pause();

        assertThatThrownBy(() -> loader(10).load(job, street)).isInstanceOf(InterruptedException.class);
        verifyNoInteractions(client, store, streetStore);
    }

    private RepairStreetLoader loader(int maxPages) {
        return new RepairStreetLoader(client, store, delay,
                new RepairApiProperties(URI.create("http://localhost"), Duration.ofSeconds(1), Duration.ofSeconds(1), maxPages),
                streetStore);
    }

    private RepairApiPage page(long id) {
        var house = new RepairResponse.House();
        house.setGuid(new UUID(0, id));
        return new RepairApiPage(new RepairResponse(List.of(house), 1L, null), "{}");
    }
}

package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import ru.repairradar.dto.ProgramApiPage;
import ru.repairradar.dto.ProgramResponse;
import ru.repairradar.exception.ProgramImportException;
import ru.repairradar.service.ProgramApiClient;
import ru.repairradar.service.ProgramHouseLoader;
import ru.repairradar.service.ProgramStore;
import ru.repairradar.utility.RepairRequestDelay;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgramHouseLoaderTest {

    private final ProgramApiClient client = mock(ProgramApiClient.class);
    private final ProgramStore store = mock(ProgramStore.class);
    private final RepairRequestDelay delay = mock(RepairRequestDelay.class);
    private final UUID house = UUID.randomUUID();

    @Test
    void pausesFetchesSavesAndReturnsWorkCount() throws Exception {
        var response = new ProgramResponse(UUID.randomUUID(), UUID.randomUUID(), "адрес",
                List.of(work(1), work(2)));
        when(client.fetch(house)).thenReturn(new ProgramApiPage(response, "{\"works\":[]}"));

        assertThat(loader().load(house)).isEqualTo(2);

        var order = inOrder(delay, client, store);
        order.verify(delay).pause();
        order.verify(client).fetch(house);
        order.verify(store).save(eq(house), eq(response), eq("{\"works\":[]}"));
        verifyNoMoreInteractions(client, store);
        verifyNoMoreInteractions(delay);
    }

    @Test
    void upstreamErrorIsWrappedWithHouseGuidContext() throws Exception {
        when(client.fetch(house)).thenThrow(new ProgramImportException("Вышестоящий сервис вернул HTTP 503"));

        assertThatThrownBy(() -> loader().load(house)).isInstanceOf(ProgramImportException.class)
                .hasMessageContaining("houseGuid=" + house).hasMessageContaining("HTTP 503");
    }

    @Test
    void interruptedDelayStopsBeforeTheRequest() throws Exception {
        doThrow(new InterruptedException("прервано")).when(delay).pause();

        assertThatThrownBy(() -> loader().load(house)).isInstanceOf(InterruptedException.class);
        verifyNoInteractions(client, store);
    }

    private ProgramHouseLoader loader() {
        return new ProgramHouseLoader(client, store, delay);
    }

    private ProgramResponse.Work work(long seed) {
        var work = new ProgramResponse.Work();
        work.setGuid(new UUID(0, seed));
        work.setWorkNumber(seed);
        work.setStartDate("01.2030");
        work.setEndDate("12.2032");
        var type = new ProgramResponse.WorkType();
        type.setCapitalRepairWorkTypeName("вид работ");
        work.setWorkType(type);
        return work;
    }
}
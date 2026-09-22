package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.repairradar.dto.ProgramResponse;
import ru.repairradar.entity.ProgramHouse;
import ru.repairradar.mapper.ProgramMapperImpl;
import ru.repairradar.repository.ProgramHouseRepository;
import ru.repairradar.repository.RepairHouseRepository;
import ru.repairradar.service.ProgramStore;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProgramStoreTest {

    @Test
    void persistsHouseWithBodyAndMarksRepairHouseLoaded() throws IOException {
        var houses = mock(ProgramHouseRepository.class);
        var repairHouses = mock(RepairHouseRepository.class);
        var store = new ProgramStore(houses, repairHouses, new ProgramMapperImpl());
        UUID repairGuid = UUID.randomUUID();
        String body = new String(getClass().getResourceAsStream("/program-response.json").readAllBytes());

        ProgramResponse response = new tools.jackson.databind.ObjectMapper().readValue(body, ProgramResponse.class);
        store.save(repairGuid, response, body);

        ArgumentCaptor<ProgramHouse> captor = ArgumentCaptor.forClass(ProgramHouse.class);
        verify(houses).save(captor.capture());
        assertThat(captor.getValue().getHouseGuid()).isEqualTo(repairGuid);
        assertThat(captor.getValue().getPayload()).isEqualTo(body);
        assertThat(captor.getValue().getWorks()).hasSize(2);
        verify(repairHouses).markProgramLoaded(repairGuid);
    }
}
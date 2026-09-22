package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.repairradar.dto.ProgramApiPage;
import ru.repairradar.exception.ProgramImportException;
import ru.repairradar.utility.RepairRequestDelay;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgramHouseLoader {

    private final ProgramApiClient client;
    private final ProgramStore store;
    private final RepairRequestDelay delay;

    public int load(UUID houseGuid) throws InterruptedException {
        delay.pause();
        try {
            ProgramApiPage page = client.fetch(houseGuid);
            store.save(houseGuid, page.response(), page.body());
            return page.response().works().size();
        } catch (RuntimeException e) {
            throw new ProgramImportException("houseGuid=" + houseGuid + ": " + e.getMessage(), e);
        }
    }
}
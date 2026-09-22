package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.repairradar.config.RepairApiProperties;
import ru.repairradar.dto.RepairResponse;
import ru.repairradar.exception.RepairImportException;
import ru.repairradar.utility.RepairRequestDelay;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepairStreetLoader {

    private final RepairApiClient client;
    private final RepairPageStore store;
    private final RepairRequestDelay delay;
    private final RepairApiProperties properties;
    private final StreetStore streetStore;

    public void load(UUID jobId, UUID streetGuid) throws InterruptedException {
        Set<UUID> seen = new HashSet<>();
        for (int index = 1; index <= properties.maxPages(); index++) {
            delay.pause();
            try {
                var page = client.fetch(streetGuid, index);
                if (page.response().items().isEmpty()) {
                    streetStore.markRepairDataLoaded(streetGuid);
                    return;
                }
                requireProgress(page.response(), seen);
                store.save(jobId, streetGuid, index, page.response(), page.body());
            } catch (RuntimeException e) {
                throw new RepairImportException("streetGuid=" + streetGuid + ", pageIndex=" + index
                        + ": " + e.getMessage(), e);
            }
        }
        throw new RepairImportException("streetGuid=" + streetGuid + ": maxPages=" + properties.maxPages()
                + " reached before an empty page; import incomplete");
    }

    private void requireProgress(RepairResponse response, Set<UUID> seen) {
        int before = seen.size();
        for (var item : response.items()) {
            seen.add(item.getGuid());
        }
        if (seen.size() == before) {
            throw new RepairImportException("Nonempty page repeated previously received GUIDs; import incomplete");
        }
    }
}

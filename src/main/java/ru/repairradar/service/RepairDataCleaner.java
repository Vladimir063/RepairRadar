package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repairradar.repository.*;

@Service
@RequiredArgsConstructor
public class RepairDataCleaner {

    private final RepairKprWorkRepository kprWorks;
    private final RepairRegionalWorkRepository regionalWorks;
    private final RepairHouseRepository houses;
    private final RepairWorkGroupRepository groups;
    private final RepairPageRepository pages;
    private final RepairJobRepository jobs;

    @Transactional
    public void clear() {
        kprWorks.deleteAllInBatch();
        regionalWorks.deleteAllInBatch();
        houses.deleteAllInBatch();
        groups.deleteAllInBatch();
        pages.deleteAllInBatch();
        jobs.deleteAllInBatch();
    }
}

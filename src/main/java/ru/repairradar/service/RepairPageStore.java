package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repairradar.dto.RepairResponse;
import ru.repairradar.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import ru.repairradar.mapper.RepairMapper;
import ru.repairradar.repository.*;

import java.util.*;

@Service
@Validated
@RequiredArgsConstructor
public class RepairPageStore {

    private final RepairHouseRepository houses;
    private final RepairPageRepository pages;
    private final RepairJobRepository jobs;
    private final RepairMapper mapper;

    @Transactional
    public void save(UUID jobId, UUID streetGuid, int pageIndex, @NotNull @Valid RepairResponse response, String body) {
        var entities = mapper.toHouses(response);
        houses.saveAll(entities);
        savePage(jobId, streetGuid, pageIndex, response, body);
        RepairJob job = jobs.findById(jobId).orElseThrow();
        job.setPagesSaved(job.getPagesSaved() + 1);
        job.setItemsSaved(job.getItemsSaved() + response.items().size());
    }

    private void savePage(UUID jobId, UUID streetGuid, int index, RepairResponse response, String body) {
        var page = new RepairPage();
        page.setId(UUID.randomUUID());
        page.setJobId(jobId);
        page.setStreetGuid(streetGuid);
        page.setPageIndex(index);
        page.setSourceCount(response.count());
        page.setResponseTimestamp(response.responseTimestamp());
        page.setPayload(body);
        pages.save(page);
    }

}

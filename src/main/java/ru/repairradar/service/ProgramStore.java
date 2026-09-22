package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repairradar.dto.ProgramResponse;
import ru.repairradar.entity.ProgramHouse;
import ru.repairradar.mapper.ProgramMapper;
import ru.repairradar.repository.ProgramHouseRepository;
import ru.repairradar.repository.RepairHouseRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgramStore {

    private final ProgramHouseRepository houses;
    private final RepairHouseRepository repairHouses;
    private final ProgramMapper mapper;

    @Transactional
    public void save(UUID houseGuid, ProgramResponse response, String body) {
        ProgramHouse house = mapper.toHouse(response);
        house.setHouseGuid(houseGuid);
        house.setPayload(body);
        houses.save(house);
        repairHouses.markProgramLoaded(houseGuid);
    }
}
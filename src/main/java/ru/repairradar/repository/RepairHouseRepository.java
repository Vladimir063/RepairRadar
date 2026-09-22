package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.repairradar.entity.RepairHouse;

import java.util.List;
import java.util.UUID;

public interface RepairHouseRepository extends JpaRepository<RepairHouse, UUID> {

    @Query("select h.guid from RepairHouse h where h.programName like 'Региональная%' and h.programDataLoaded = false")
    List<UUID> findProgramImportCandidates();

    @Modifying
    @Query("update RepairHouse h set h.programDataLoaded = true where h.guid = :houseGuid")
    void markProgramLoaded(@Param("houseGuid") UUID houseGuid);
}
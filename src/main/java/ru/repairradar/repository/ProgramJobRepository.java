package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.entity.ProgramJob;

import java.util.List;
import java.util.UUID;

public interface ProgramJobRepository extends JpaRepository<ProgramJob, UUID> {

    List<ProgramJob> findByStatusIn(List<RepairJobStatus> statuses);
}
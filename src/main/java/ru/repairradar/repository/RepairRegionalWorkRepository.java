package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.repairradar.entity.RepairRegionalWork;
import java.util.UUID;

public interface RepairRegionalWorkRepository extends JpaRepository<RepairRegionalWork, UUID> {
}

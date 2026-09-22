package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.repairradar.entity.RepairPage;

import java.util.UUID;

public interface RepairPageRepository extends JpaRepository<RepairPage, UUID> {
}

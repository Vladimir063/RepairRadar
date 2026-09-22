package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.repairradar.entity.ProgramHouse;

import java.util.UUID;

public interface ProgramHouseRepository extends JpaRepository<ProgramHouse, UUID> {
}
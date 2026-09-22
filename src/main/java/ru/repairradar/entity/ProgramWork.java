package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "program_works", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class ProgramWork {

    @Id
    @Column(name = "guid")
    private UUID guid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "house_guid", nullable = false)
    private ProgramHouse house;

    @Column(name = "work_number")
    private Long workNumber;

    @Column(name = "capital_repair_work_type_name", columnDefinition = "text")
    private String capitalRepairWorkTypeName;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
}
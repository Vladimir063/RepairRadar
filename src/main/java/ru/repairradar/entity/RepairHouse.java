package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "repair_houses", schema = "public")
@Getter
@Setter
public class RepairHouse extends RepairMetadata {

    @Column(name = "status", columnDefinition = "text")
    private String status;

    @Column(name = "program_guid")
    private UUID programGuid;

    @Column(name = "program_name", columnDefinition = "text")
    private String programName;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "works_number")
    private Integer worksNumber;

    @OneToMany(mappedBy = "owner", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<RepairRegionalWork> regionalWorks = new ArrayList<>();

    @OneToMany(mappedBy = "owner", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<RepairKprWork> kprWorks = new ArrayList<>();

    @Embedded
    private RepairProgramType programType;

    @Column(name = "program_data_loaded", nullable = false, updatable = false)
    private boolean programDataLoaded;
}

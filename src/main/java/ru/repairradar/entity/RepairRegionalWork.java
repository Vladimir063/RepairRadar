package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "repair_regional_works", schema = "public")
@Getter
@Setter
public class RepairRegionalWork extends RepairMetadata {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_guid", nullable = false)
    private RepairHouse owner;

    @Column(name = "work_number")
    private Long workNumber;

    @Column(name = "work_type_code", columnDefinition = "text")
    private String workTypeCode;

    @Column(name = "work_group_code", columnDefinition = "text")
    private String workGroupCode;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "fias_house_guid")
    private UUID fiasHouseGuid;

    @Column(name = "oktmo_code", columnDefinition = "text")
    private String oktmoCode;

    @Column(name = "region_guid")
    private UUID regionGuid;

    @Column(name = "work_type_name", columnDefinition = "text")
    private String workTypeName;

    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "work_group_guid")
    private RepairWorkGroup workGroup;

}

package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "repair_kpr_works", schema = "public")
@Getter
@Setter
public class RepairKprWork {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_guid", nullable = false)
    private RepairHouse owner;

    @Id
    @Column(name = "guid")
    private UUID guid;

    @Column(name = "last_editing_date")
    private Long lastEditingDate;

    @Column(name = "from_excel")
    private Boolean fromExcel;

    @Column(name = "work_number")
    private Long workNumber;

    @Column(name = "work_type_code", columnDefinition = "text")
    private String workTypeCode;

    @Column(name = "work_group_code", columnDefinition = "text")
    private String workGroupCode;

    @Column(name = "work_type_name", columnDefinition = "text")
    private String workTypeName;

    @Column(name = "house_guid")
    private UUID houseGuid;

    @Column(name = "fias_house_guid")
    private UUID fiasHouseGuid;

    @Column(name = "house_address", columnDefinition = "text")
    private String houseAddress;

    @Column(name = "oktmo_code", columnDefinition = "text")
    private String oktmoCode;

    @Column(name = "region_guid")
    private UUID regionGuid;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "fund_sum", columnDefinition = "numeric")
    private BigDecimal fundSum;

    @Column(name = "subject_sum", columnDefinition = "numeric")
    private BigDecimal subjectSum;

    @Column(name = "local_sum", columnDefinition = "numeric")
    private BigDecimal localSum;

    @Column(name = "owner_sum", columnDefinition = "numeric")
    private BigDecimal ownerSum;

    @Column(name = "total_sum", columnDefinition = "numeric")
    private BigDecimal totalSum;

    @Column(name = "specific_cost", columnDefinition = "numeric")
    private BigDecimal specificCost;

    @Column(name = "maximum_cost", columnDefinition = "numeric")
    private BigDecimal maximumCost;

    @Column(name = "contracted_sum", columnDefinition = "numeric")
    private BigDecimal contractedSum;

    @Column(name = "complete_percent", columnDefinition = "numeric")
    private BigDecimal completePercent;

    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "work_group_guid")
    private RepairWorkGroup workGroup;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "contracts", columnDefinition = "jsonb")
    private String contracts;
}

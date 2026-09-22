package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "repair_work_groups", schema = "public")
@Getter
@Setter
public class RepairWorkGroup {

    @Id
    @Column(name = "guid")
    private UUID guid;

    @Column(name = "code", columnDefinition = "text")
    private String code;

    @Column(name = "root_entity_guid")
    private UUID rootEntityGuid;

    @Column(name = "actual")
    private Boolean actual;

    @Column(name = "last_update_date", columnDefinition = "text")
    private String lastUpdateDate;

    @Column(name = "create_date", columnDefinition = "text")
    private String createDate;

    @Column(name = "name", columnDefinition = "text")
    private String name;

}

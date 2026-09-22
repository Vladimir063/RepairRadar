package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "program_houses", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class ProgramHouse {

    @Id
    @Column(name = "house_guid")
    private UUID houseGuid;

    @Column(name = "program_guid")
    private UUID programGuid;

    @Column(name = "house_address", nullable = false, columnDefinition = "text")
    private String houseAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @OneToMany(mappedBy = "house", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<ProgramWork> works = new ArrayList<>();
}
package ru.repairradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "streets", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class Street {

    @Id
    @Column(name = "street_guid", nullable = false, updatable = false)
    private UUID streetGuid;

    @Column(name = "street_object_id", nullable = false)
    private long streetObjectId;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "city", nullable = false, columnDefinition = "text")
    private String city;

    @Column(name = "full_address", columnDefinition = "text")
    private String fullAddress;

    @Column(name = "locality", columnDefinition = "text")
    private String locality;

    @Column(name = "hierarchy_path", columnDefinition = "text")
    private String hierarchyPath;

    @Column(name = "repair_data_loaded", nullable = false)
    private boolean repairDataLoaded;
}

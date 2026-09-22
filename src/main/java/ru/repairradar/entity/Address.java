package ru.repairradar.entity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "addresses", schema = "public")
@Access(AccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
public class Address {

    @Id
    @Column(name = "house_guid", nullable = false, updatable = false)
    private UUID houseGuid;

    @Column(name = "city", nullable = false, columnDefinition = "text")
    private String city;

    @Column(name = "full_address", nullable = false, columnDefinition = "text")
    private String fullAddress;

    @Column(name = "street", nullable = false, columnDefinition = "text")
    private String street;

    @Column(name = "house_number", nullable = false, columnDefinition = "text")
    private String houseNumber;

    @Column(name = "additional_number_1", columnDefinition = "text")
    private String additionalNumber1;

    @Column(name = "additional_type_1")
    private Integer additionalType1;

    @Column(name = "additional_number_2", columnDefinition = "text")
    private String additionalNumber2;

    @Column(name = "additional_type_2")
    private Integer additionalType2;

    @Column(name = "house_type")
    private Integer houseType;

    @Column(name = "street_object_id", nullable = false)
    private long streetObjectId;

    @Column(name = "street_guid", nullable = false)
    private UUID streetGuid;

    @Column(name = "house_object_id", nullable = false)
    private long houseObjectId;
}

package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.repairradar.entity.Address;

import java.util.List;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    @Modifying
    @Query(value = "LOCK TABLE public.addresses IN EXCLUSIVE MODE", nativeQuery = true)
    void lockForWrite();

    @Query("select a.houseGuid from Address a")
    List<UUID> findAllHouseGuids();

    @Modifying
    @Query(value = """
            INSERT INTO public.addresses (
                city, full_address, street, house_number,
                additional_number_1, additional_type_1,
                additional_number_2, additional_type_2, house_type,
                street_object_id, street_guid, house_object_id, house_guid
            ) VALUES (
                :#{#address.city}, :#{#address.fullAddress}, :#{#address.street}, :#{#address.houseNumber},
                :#{#address.additionalNumber1}, :#{#address.additionalType1},
                :#{#address.additionalNumber2}, :#{#address.additionalType2}, :#{#address.houseType},
                :#{#address.streetObjectId}, :#{#address.streetGuid},
                :#{#address.houseObjectId}, :#{#address.houseGuid}
            ) ON CONFLICT (house_guid) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("address") Address address);
}

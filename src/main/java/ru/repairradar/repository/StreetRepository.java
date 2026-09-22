package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.repairradar.entity.Street;

import java.util.UUID;
import java.util.List;

public interface StreetRepository extends JpaRepository<Street, UUID> {

    @Modifying
    @Query(value = "LOCK TABLE public.streets IN EXCLUSIVE MODE", nativeQuery = true)
    void lockForWrite();

    @Query("select s.streetGuid from Street s")
    List<UUID> findAllStreetGuids();

    @Query("select s.streetGuid from Street s where s.repairDataLoaded = false")
    List<UUID> findUnloadedStreetGuids();

    @Modifying
    @Query("update Street s set s.repairDataLoaded = true where s.streetGuid = :streetGuid")
    void markRepairDataLoaded(@Param("streetGuid") UUID streetGuid);

    @Modifying
    @Query(value = """
            INSERT INTO public.streets (street_guid, street_object_id, name, city, full_address, locality, hierarchy_path)
            VALUES (:#{#street.streetGuid}, :#{#street.streetObjectId}, :#{#street.name}, :#{#street.city},
                    :#{#street.fullAddress}, :#{#street.locality}, :#{#street.hierarchyPath})
            ON CONFLICT (street_guid) DO UPDATE SET
                street_object_id = EXCLUDED.street_object_id,
                name = EXCLUDED.name,
                city = EXCLUDED.city,
                full_address = EXCLUDED.full_address,
                locality = EXCLUDED.locality,
                hierarchy_path = EXCLUDED.hierarchy_path
            WHERE (streets.street_object_id, streets.name, streets.city,
                   streets.full_address, streets.locality, streets.hierarchy_path)
                IS DISTINCT FROM (EXCLUDED.street_object_id, EXCLUDED.name, EXCLUDED.city,
                                  EXCLUDED.full_address, EXCLUDED.locality, EXCLUDED.hierarchy_path)
            """, nativeQuery = true)
    int upsert(@Param("street") Street street);
}

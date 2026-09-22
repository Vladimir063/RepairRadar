package ru.repairradar.mapper;

import org.mapstruct.*;
import ru.repairradar.dto.RepairResponse;
import ru.repairradar.entity.*;
import tools.jackson.databind.JsonNode;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RepairMapper {

    default List<RepairHouse> toHouses(RepairResponse response) {
        return toHouses(response.items(), new RepairMappingContext());
    }

    List<RepairHouse> toHouses(List<RepairResponse.House> source, @Context RepairMappingContext context);

    @Mapping(target = "programDataLoaded", ignore = true)
    RepairHouse toHouse(RepairResponse.House source, @Context RepairMappingContext context);

    @Mapping(target = "owner", ignore = true)
    RepairRegionalWork toRegionalWork(RepairResponse.RegionalWork source, @Context RepairMappingContext context);

    @Mapping(target = "owner", ignore = true)
    RepairKprWork toKprWork(RepairResponse.KprWork source, @Context RepairMappingContext context);

    default RepairWorkGroup toGroup(RepairResponse.WorkGroup source, @Context RepairMappingContext context) {
        if (source == null) {
            return null;
        }
        RepairWorkGroup group = context.group(source.getGuid());
        updateGroup(source, group);
        return group;
    }

    void updateGroup(RepairResponse.WorkGroup source, @MappingTarget RepairWorkGroup target);

    @AfterMapping
    default void attachWorks(@MappingTarget RepairHouse house) {
        if (house.getRegionalWorks() != null) {
            house.getRegionalWorks().forEach(work -> work.setOwner(house));
        }
        if (house.getKprWorks() != null) {
            house.getKprWorks().forEach(work -> work.setOwner(house));
        }
    }

    default String toJson(JsonNode node) {
        return node == null || node.isNull() ? null : node.toString();
    }
}

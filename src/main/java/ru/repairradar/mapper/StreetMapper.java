package ru.repairradar.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.repairradar.dto.StreetRow;
import ru.repairradar.entity.Street;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StreetMapper {

    @Mapping(target = "repairDataLoaded", ignore = true)
    Street toEntity(StreetRow row);
}

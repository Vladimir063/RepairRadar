package ru.repairradar.mapper;

import org.mapstruct.*;
import ru.repairradar.dto.ProgramResponse;
import ru.repairradar.entity.ProgramHouse;
import ru.repairradar.entity.ProgramWork;
import ru.repairradar.utility.ProgramDates;

import java.time.LocalDate;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProgramMapper {

    @Mapping(target = "houseGuid", ignore = true)
    @Mapping(target = "programGuid", source = "guid")
    @Mapping(target = "payload", ignore = true)
    ProgramHouse toHouse(ProgramResponse source);

    @Mapping(target = "house", ignore = true)
    @Mapping(target = "capitalRepairWorkTypeName", source = "workType.capitalRepairWorkTypeName")
    @Mapping(target = "startDate", source = "startDate", qualifiedByName = "parseStart")
    @Mapping(target = "endDate", source = "endDate", qualifiedByName = "parseEnd")
    ProgramWork toWork(ProgramResponse.Work source);

    @AfterMapping
    default void attachHouse(@MappingTarget ProgramHouse house) {
        if (house.getWorks() != null) {
            house.getWorks().forEach(work -> work.setHouse(house));
        }
    }

    @Named("parseStart")
    default LocalDate parseStart(String value) {
        return ProgramDates.start(value);
    }

    @Named("parseEnd")
    default LocalDate parseEnd(String value) {
        return ProgramDates.end(value);
    }
}
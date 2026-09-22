package ru.repairradar.mapper;

import org.mapstruct.Mapper;
import ru.repairradar.dto.AddressRow;
import ru.repairradar.entity.Address;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    Address toEntity(AddressRow row);
}

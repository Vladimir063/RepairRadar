package ru.repairradar.mapper;

import ru.repairradar.entity.RepairWorkGroup;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RepairMappingContext {

    private final Map<UUID, RepairWorkGroup> groups = new HashMap<>();

    public RepairWorkGroup group(UUID guid) {
        return groups.computeIfAbsent(guid, key -> new RepairWorkGroup());
    }
}

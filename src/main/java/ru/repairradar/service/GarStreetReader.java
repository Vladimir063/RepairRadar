package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.repairradar.dto.GarFiles;
import ru.repairradar.dto.GarObject;
import ru.repairradar.dto.StreetRow;
import ru.repairradar.utility.GarXmlReader;
import ru.repairradar.utility.StreetFormatter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class GarStreetReader {

    private static final int STREET_LEVEL = 8;
    private static final String MOSCOW = "Москва";

    private final GarXmlReader xmlReader;
    private final StreetFormatter formatter;

    public List<StreetRow> readCandidates(Path directory) {
        GarFiles files = GarFiles.discoverStreets(directory);
        Map<Long, GarObject> objects = readObjects(files.objects());
        Map<UUID, StreetRow> streets = new LinkedHashMap<>();
        xmlReader.read(files.hierarchy(), "ITEMS", "ITEM", attributes -> {
            if (!"1".equals(attributes.get("ISACTIVE")) || !"77".equals(attributes.get("REGIONCODE"))) {
                return;
            }
            readStreet(attributes, objects, streets);
        });
        log.info("GAR street read complete: unique streets={}", streets.size());
        return new ArrayList<>(streets.values());
    }

    private Map<Long, GarObject> readObjects(Path file) {
        Map<Long, GarObject> objects = new HashMap<>();
        xmlReader.read(file, "ADDRESSOBJECTS", "OBJECT", attributes -> {
            if (!"1".equals(attributes.get("ISACTIVE")) || !"1".equals(attributes.get("ISACTUAL"))) {
                return;
            }
            try {
                GarObject object = parseObject(attributes);
                objects.putIfAbsent(object.objectId(), object);
            } catch (IllegalArgumentException e) {
                log.debug("Skipping invalid GAR object: OBJECTID={}", attributes.get("OBJECTID"));
            }
        });
        return objects;
    }

    private void readStreet(Map<String, String> attributes, Map<Long, GarObject> objects,
                            Map<UUID, StreetRow> streets) {
        try {
            long objectId = Long.parseLong(required(attributes, "OBJECTID"));
            GarObject street = objects.get(objectId);
            if (street == null || street.level() != STREET_LEVEL) {
                return;
            }
            List<GarObject> chain = resolveChain(required(attributes, "PATH"), objectId, objects);
            String city = resolveCity(chain);
            streets.putIfAbsent(street.guid(), formatter.toRow(chain, city));
        } catch (IllegalArgumentException e) {
            log.debug("Skipping invalid GAR street hierarchy: OBJECTID={}, reason={}",
                    attributes.get("OBJECTID"), e.getMessage());
        }
    }

    private List<GarObject> resolveChain(String path, long streetId, Map<Long, GarObject> objects) {
        String[] ids = path.split("\\.", -1);
        if (ids.length < 2 || Long.parseLong(ids[ids.length - 1]) != streetId) {
            throw new IllegalArgumentException("Invalid street path");
        }
        var seen = new HashSet<Long>();
        var chain = new ArrayList<GarObject>();
        for (String id : ids) {
            long objectId = Long.parseLong(id);
            GarObject object = objects.get(objectId);
            if (object == null || !seen.add(objectId)) {
                throw new IllegalArgumentException("Missing or repeated ancestor");
            }
            chain.add(object);
        }
        return chain;
    }

    private String resolveCity(List<GarObject> chain) {
        GarObject root = chain.getFirst();
        if (root.level() != 1 || !MOSCOW.equals(root.name())) {
            throw new IllegalArgumentException("Not a Moscow root");
        }
        return MOSCOW;
    }

    private GarObject parseObject(Map<String, String> attributes) {
        String guidValue = required(attributes, "OBJECTGUID");
        UUID guid = UUID.fromString(guidValue);
        if (!guid.toString().equalsIgnoreCase(guidValue)) {
            throw new IllegalArgumentException("Noncanonical GUID");
        }
        return new GarObject(Long.parseLong(required(attributes, "OBJECTID")), guid,
                required(attributes, "NAME"), required(attributes, "TYPENAME"),
                Integer.parseInt(required(attributes, "LEVEL")));
    }

    private String required(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return value.strip();
    }
}

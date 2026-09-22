package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.repairradar.dto.AddressRow;
import ru.repairradar.dto.GarFiles;
import ru.repairradar.dto.GarHouse;
import ru.repairradar.dto.GarObject;
import ru.repairradar.utility.AddressFormatter;
import ru.repairradar.utility.GarXmlReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class GarAddressReader {

    private static final String MOSCOW = "Москва";
    private static final String MOSCOW_REGION_CODE = "77";
    private static final int STREET_LEVEL = 8;

    private final GarXmlReader xmlReader;
    private final AddressFormatter formatter;

    public List<AddressRow> readCandidates(Path directory) {
        GarFiles files = GarFiles.discover(directory);
        Rejected rejected = new Rejected();
        Map<Long, GarObject> objects = readObjects(files, rejected);
        Map<Long, GarHouse> houses = readHouses(files, objects.size(), rejected);
        List<AddressRow> candidates = readHierarchy(files, objects, houses, rejected);
        log.info("GAR read complete: unique candidates={}, rejected records={}, reasons=[{}]",
                candidates.size(), rejected.total(), rejected.summary());
        return candidates;
    }

    private Map<Long, GarObject> readObjects(GarFiles files, Rejected rejected) {
        Map<Long, GarObject> objects = new HashMap<>();
        Path file = files.objects();
        log.info("Reading GAR objects from {}", file.getFileName());
        xmlReader.read(file, "ADDRESSOBJECTS", "OBJECT", attributes -> {
            if (!active(attributes)) {
                return;
            }
            try {
                GarObject object = parseObject(attributes);
                keepFirst(objects, object.objectId(), object, file);
            } catch (IllegalArgumentException e) {
                rejected.add(RejectReason.INVALID_OBJECT, attributes, file);
            }
        });
        return objects;
    }

    private Map<Long, GarHouse> readHouses(GarFiles files, int activeObjects, Rejected rejected) {
        Map<Long, GarHouse> houses = new HashMap<>();
        Path file = files.houses();
        log.info("Reading GAR houses from {}; active objects={}", file.getFileName(), activeObjects);
        xmlReader.read(file, "HOUSES", "HOUSE", attributes -> {
            if (!active(attributes)) {
                return;
            }
            try {
                GarHouse house = parseHouse(attributes);
                keepFirst(houses, house.objectId(), house, file);
            } catch (IllegalArgumentException e) {
                rejected.add(RejectReason.INVALID_HOUSE, attributes, file);
            }
        });
        return houses;
    }

    private List<AddressRow> readHierarchy(GarFiles files, Map<Long, GarObject> objects,
                                           Map<Long, GarHouse> houses, Rejected rejected) {
        List<AddressRow> candidates = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Path file = files.hierarchy();
        log.info("Streaming GAR hierarchy from {}; active numbered houses={}", file.getFileName(), houses.size());
        xmlReader.read(file, "ITEMS", "ITEM", attributes -> {
            if (!"1".equals(attributes.get("ISACTIVE")) || !MOSCOW_REGION_CODE.equals(attributes.get("REGIONCODE"))) {
                return;
            }
            try {
                GarHouse house = houses.get(id(attributes));
                if (house == null || seen.contains(house.guid())) {
                    return;
                }
                buildCandidate(attributes, house, objects, seen, candidates, file, rejected);
            } catch (IllegalArgumentException e) {
                rejected.add(RejectReason.INVALID_ITEM, attributes, file);
            }
        });
        return candidates;
    }

    private void buildCandidate(Map<String, String> attributes, GarHouse house, Map<Long, GarObject> objects,
                                Set<UUID> seen, List<AddressRow> candidates, Path file, Rejected rejected) {
        Chain chain = resolveChain(attributes, house, objects, file, rejected);
        if (chain == null) {
            return;
        }
        if (!isMoscowRoot(chain.ancestors())) {
            rejected.add(RejectReason.NOT_MOSCOW_ROOT, attributes, file);
            return;
        }
        seen.add(house.guid());
        candidates.add(new AddressRow(MOSCOW, formatter.format(chain.ancestors(), house), chain.street().label(),
                house.number(), house.additionalNumber1(), house.additionalType1(),
                house.additionalNumber2(), house.additionalType2(), house.type(),
                chain.street().objectId(), chain.street().guid(), house.objectId(), house.guid()));
    }

    private static Chain resolveChain(Map<String, String> attributes, GarHouse house, Map<Long, GarObject> objects,
                                      Path file, Rejected rejected) {
        String[] path = required(attributes, "PATH").split("\\.");
        if (path.length < 3 || Long.parseLong(path[path.length - 1]) != house.objectId()) {
            rejected.add(RejectReason.BROKEN_PATH, attributes, file);
            return null;
        }
        List<GarObject> ancestors = new ArrayList<>();
        Set<Long> chain = new HashSet<>();
        chain.add(house.objectId());
        GarObject street = null;
        for (int i = 0; i < path.length - 1; i++) {
            long parentId = Long.parseLong(path[i]);
            GarObject parent = objects.get(parentId);
            if (parent == null) {
                rejected.add(RejectReason.MISSING_PARENT, attributes, file);
                return null;
            }
            if (!chain.add(parentId)) {
                rejected.add(RejectReason.DUPLICATE_ANCESTOR, attributes, file);
                return null;
            }
            ancestors.add(parent);
            if (parent.level() == STREET_LEVEL) {
                street = parent;
            }
        }
        if (street == null) {
            rejected.add(RejectReason.NO_STREET, attributes, file);
            return null;
        }
        return new Chain(ancestors, street);
    }

    private static boolean isMoscowRoot(List<GarObject> ancestors) {
        GarObject root = ancestors.getFirst();
        return root.level() == 1 && MOSCOW.equals(root.name());
    }

    private static GarObject parseObject(Map<String, String> attributes) {
        return new GarObject(id(attributes), guid(attributes), required(attributes, "NAME"),
                required(attributes, "TYPENAME"), Integer.parseInt(required(attributes, "LEVEL")));
    }

    private static GarHouse parseHouse(Map<String, String> attributes) {
        return new GarHouse(id(attributes), guid(attributes), required(attributes, "HOUSENUM"),
                integer(attributes, "HOUSETYPE"), optional(attributes, "ADDNUM1"),
                integer(attributes, "ADDTYPE1"), optional(attributes, "ADDNUM2"),
                integer(attributes, "ADDTYPE2"));
    }

    private static <T> void keepFirst(Map<Long, T> byId, long id, T value, Path file) {
        T previous = byId.putIfAbsent(id, value);
        if (previous != null && !previous.equals(value) && log.isDebugEnabled()) {
            log.debug("Duplicate OBJECTID={} with differing values in {}; keeping the first",
                    id, file.getFileName());
        }
    }

    private static boolean active(Map<String, String> attributes) {
        return "1".equals(attributes.get("ISACTIVE")) && "1".equals(attributes.get("ISACTUAL"));
    }

    private static long id(Map<String, String> attributes) {
        return Long.parseLong(required(attributes, "OBJECTID"));
    }

    private static UUID guid(Map<String, String> attributes) {
        String value = required(attributes, "OBJECTGUID");
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Noncanonical GUID");
        }
        return parsed;
    }

    private static String optional(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String required(Map<String, String> attributes, String key) {
        String value = optional(attributes, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return value;
    }

    private static Integer integer(Map<String, String> attributes, String key) {
        String value = optional(attributes, key);
        return value == null ? null : Integer.valueOf(value);
    }

    private record Chain(List<GarObject> ancestors, GarObject street) {
    }

    private enum RejectReason {
        INVALID_OBJECT, INVALID_HOUSE, INVALID_ITEM, BROKEN_PATH,
        MISSING_PARENT, DUPLICATE_ANCESTOR, NO_STREET, NOT_MOSCOW_ROOT;
    }

    private static final class Rejected {

        private final EnumMap<RejectReason, Integer> counts = new EnumMap<>(RejectReason.class);

        void add(RejectReason reason, Map<String, String> attributes, Path file) {
            counts.merge(reason, 1, Integer::sum);
            if (log.isTraceEnabled()) {
                log.trace("Rejected GAR record: reason={}, OBJECTID={}, file={}",
                        reason, attributes.get("OBJECTID"), file.getFileName());
            }
        }

        int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        String summary() {
            if (counts.isEmpty()) {
                return "none";
            }
            return counts.entrySet().stream()
                    .sorted(Map.Entry.<RejectReason, Integer>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(entry -> entry.getKey().name()))
                    .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));
        }
    }
}
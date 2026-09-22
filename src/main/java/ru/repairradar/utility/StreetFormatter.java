package ru.repairradar.utility;

import org.springframework.stereotype.Component;
import ru.repairradar.dto.GarObject;
import ru.repairradar.dto.StreetRow;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class StreetFormatter {

    public StreetRow toRow(List<GarObject> chain, String city) {
        GarObject street = chain.getLast();
        return new StreetRow(street.guid(), street.objectId(), street.label(), city,
                fullAddress(chain), locality(chain), hierarchyPath(chain));
    }

    private String fullAddress(List<GarObject> chain) {
        return chain.reversed().stream()
                .map(GarObject::label)
                .collect(Collectors.joining(", "));
    }

    private String locality(List<GarObject> chain) {
        for (int i = chain.size() - 2; i > 0; i--) {
            GarObject ancestor = chain.get(i);
            if (ancestor.level() == 5 || ancestor.level() == 6) {
                return ancestor.label();
            }
        }
        return null;
    }

    private String hierarchyPath(List<GarObject> chain) {
        return chain.stream()
                .map(object -> Long.toString(object.objectId()))
                .collect(Collectors.joining("."));
    }
}

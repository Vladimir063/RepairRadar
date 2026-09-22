package ru.repairradar.utility;

import org.springframework.stereotype.Component;
import ru.repairradar.dto.GarHouse;
import ru.repairradar.dto.GarObject;

import java.util.ArrayList;
import java.util.List;

@Component
public class AddressFormatter {

    public String format(List<GarObject> ancestors, GarHouse house) {
        var parts = new ArrayList<String>();
        ancestors.forEach(ancestor -> parts.add(ancestor.label()));
        parts.add(houseLabel(house) + " " + house.number());
        extra(parts, house.additionalNumber1(), house.additionalType1());
        extra(parts, house.additionalNumber2(), house.additionalType2());
        return String.join(", ", parts);
    }

    private static String houseLabel(GarHouse house) {
        if (house.type() == null) {
            return "дом";
        }
        return switch (house.type()) {
            case 2 -> "д.";
            default -> "тип дома " + house.type();
        };
    }

    private void extra(List<String> parts, String number, Integer type) {
        if (number == null || number.isBlank()) {
            return;
        }
        String label = type == null ? "доп. номер" : switch (type) {
            case 1 -> "корп.";
            case 2 -> "стр.";
            default -> "тип доп. номера " + type;
        };
        parts.add(label + " " + number);
    }
}

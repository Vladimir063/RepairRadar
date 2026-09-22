package ru.repairradar.gar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class GarStreetFixture {

    private GarStreetFixture() {
    }

    public static void write(Path directory, int count) throws IOException {
        var objects = new StringBuilder("<ADDRESSOBJECTS>");
        objects.append(object(1, "Москва", "г.", 1));
        objects.append(object(2, "Троицк", "г.", 5));
        var hierarchy = new StringBuilder("<ITEMS>");
        for (int i = 0; i < count; i++) {
            long id = 100L + i;
            objects.append(object(id, "Тестовая " + i, "ул.", 8));
            hierarchy.append(item(id, "1.2." + id));
        }
        Files.writeString(directory.resolve("AS_ADDR_OBJ_20260917_fixture.XML"), objects.append("</ADDRESSOBJECTS>"));
        Files.writeString(directory.resolve("AS_ADM_HIERARCHY_20260917_fixture.XML"), hierarchy.append("</ITEMS>"));
    }

    public static String object(long id, String name, String type, int level) {
        return """
                <OBJECT OBJECTID="%d" OBJECTGUID="%s" NAME="%s" TYPENAME="%s"
                        LEVEL="%d" ISACTIVE="1" ISACTUAL="1"/>
                """.formatted(id, new UUID(0, id), name, type, level);
    }

    public static String item(long id, String path) {
        return """
                <ITEM OBJECTID="%d" REGIONCODE="77" ISACTIVE="1" PATH="%s"/>
                """.formatted(id, path);
    }
}

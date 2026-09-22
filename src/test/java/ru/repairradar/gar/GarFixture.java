package ru.repairradar.gar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import ru.repairradar.dto.GarFiles;

public final class GarFixture {
    private GarFixture() {}
    public static GarFiles write(Path directory, int count) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("AS_ADDR_OBJ_20260917_fixture.XML"), """
            <ADDRESSOBJECTS>
              <OBJECT OBJECTID="1" OBJECTGUID="00000000-0000-0000-0000-000000000001" NAME="Москва" TYPENAME="г." LEVEL="1" ISACTIVE="1" ISACTUAL="1"/>
              <OBJECT OBJECTID="2" OBJECTGUID="00000000-0000-0000-0000-000000000002" NAME="Верхняя" TYPENAME="ул." LEVEL="8" ISACTIVE="1" ISACTUAL="1"/>
            </ADDRESSOBJECTS>
            """);
        var houses = new StringBuilder("<HOUSES>");
        var items = new StringBuilder("<ITEMS>");
        for (int i = 0; i < count; i++) {
            long id = 1000L + i;
            houses.append("""
                <HOUSE OBJECTID="%d" OBJECTGUID="%s" HOUSENUM="%d" HOUSETYPE="2" ADDNUM1="2" ADDTYPE1="1" ISACTIVE="1" ISACTUAL="1"/>
                """.formatted(id, new UUID(0, id), i + 1));
            items.append("<ITEM OBJECTID=\"%d\" REGIONCODE=\"77\" ISACTIVE=\"1\" PATH=\"1.2.%d\"/>".formatted(id, id));
        }
        Files.writeString(directory.resolve("AS_HOUSES_20260917_fixture.XML"), houses.append("</HOUSES>"));
        Files.writeString(directory.resolve("AS_ADM_HIERARCHY_20260917_fixture.XML"), items.append("</ITEMS>"));
        return GarFiles.discover(directory);
    }
}

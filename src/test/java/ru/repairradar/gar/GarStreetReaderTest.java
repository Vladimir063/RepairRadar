package ru.repairradar.gar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.repairradar.dto.GarFiles;
import ru.repairradar.dto.StreetRow;
import ru.repairradar.exception.GarImportException;
import ru.repairradar.service.GarStreetReader;
import ru.repairradar.utility.GarXmlReader;
import ru.repairradar.utility.StreetFormatter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GarStreetReaderTest {

    @TempDir
    Path directory;

    private final GarStreetReader reader = new GarStreetReader(new GarXmlReader(), new StreetFormatter());

    @Test
    void readsEveryStreetWithoutHousesAndAlwaysUsesMoscow() throws Exception {
        GarStreetFixture.write(directory, 2501);

        var rows = reader.readCandidates(directory);

        assertThat(rows).hasSize(2501);
        assertThat(rows).extracting(StreetRow::city).containsOnly("Москва");
        assertThat(rows.getFirst()).isEqualTo(new StreetRow(new UUID(0, 100), 100, "ул. Тестовая 0", "Москва",
                "ул. Тестовая 0, г. Троицк, г. Москва", "г. Троицк", "1.2.100"));
    }

    @Test
    void distinguishesSameNamedStreetsAndPreservesEveryAncestor() throws Exception {
        GarStreetFixture.write(directory, 0);
        var files = GarFiles.discoverStreets(directory);
        Files.writeString(files.objects(), "<ADDRESSOBJECTS>"
                + GarStreetFixture.object(1, "Москва", "г.", 1)
                + GarStreetFixture.object(2, "Вороново", "р-н", 2)
                + GarStreetFixture.object(3, "Кленово", "с.", 6)
                + GarStreetFixture.object(4, "ЛМС", "п.", 6)
                + GarStreetFixture.object(5, "Приозерный", "мкр.", 7)
                + GarStreetFixture.object(100, "Садовая", "ул.", 8)
                + GarStreetFixture.object(101, "Садовая", "ул.", 8)
                + GarStreetFixture.object(102, "Садовая", "ул.", 8) + "</ADDRESSOBJECTS>");
        Files.writeString(files.hierarchy(), "<ITEMS>"
                + GarStreetFixture.item(100, "1.2.3.100")
                + GarStreetFixture.item(101, "1.2.4.101")
                + GarStreetFixture.item(102, "1.2.4.5.102") + "</ITEMS>");

        var rows = reader.readCandidates(directory);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(StreetRow::name).containsOnly("ул. Садовая");
        assertThat(rows).extracting(StreetRow::fullAddress).containsExactly(
                "ул. Садовая, с. Кленово, р-н Вороново, г. Москва",
                "ул. Садовая, п. ЛМС, р-н Вороново, г. Москва",
                "ул. Садовая, мкр. Приозерный, п. ЛМС, р-н Вороново, г. Москва");
        assertThat(rows).extracting(StreetRow::locality).containsExactly("с. Кленово", "п. ЛМС", "п. ЛМС");
        assertThat(rows).extracting(StreetRow::hierarchyPath)
                .containsExactly("1.2.3.100", "1.2.4.101", "1.2.4.5.102");
    }

    @Test
    void keepsLocalityAbsentForStreetDirectlyUnderMoscow() throws Exception {
        GarStreetFixture.write(directory, 1);
        var files = GarFiles.discoverStreets(directory);
        Files.writeString(files.hierarchy(), "<ITEMS>" + GarStreetFixture.item(100, "1.100") + "</ITEMS>");

        assertThat(reader.readCandidates(directory)).containsExactly(new StreetRow(new UUID(0, 100), 100,
                "ул. Тестовая 0", "Москва", "ул. Тестовая 0, г. Москва", null, "1.100"));
    }

    @Test
    void excludesInactiveObjectsAndInvalidHierarchyAndDeduplicates() throws Exception {
        GarStreetFixture.write(directory, 0);
        var files = GarFiles.discoverStreets(directory);
        var objects = new StringBuilder(Files.readString(files.objects()).replace("</ADDRESSOBJECTS>", ""));
        for (int id = 100; id <= 110; id++) {
            String object = GarStreetFixture.object(id, "Улица", "ул.", 8);
            if (id == 101) {
                object = object.replace("ISACTUAL=\"1\"", "ISACTUAL=\"0\"");
            }
            if (id == 102) {
                object = object.replace("ISACTIVE=\"1\"", "ISACTIVE=\"0\"");
            }
            if (id == 110) {
                object = object.replace(new UUID(0, id).toString(), "bad-guid");
            }
            objects.append(object);
        }
        Files.writeString(files.objects(), objects.append("</ADDRESSOBJECTS>"));
        Files.writeString(files.hierarchy(), "<ITEMS>"
                + GarStreetFixture.item(100, "1.100").repeat(2)
                + GarStreetFixture.item(101, "1.101")
                + GarStreetFixture.item(102, "1.102")
                + GarStreetFixture.item(103, "1.103").replace("ISACTIVE=\"1\"", "ISACTIVE=\"0\"")
                + GarStreetFixture.item(104, "1.104").replace("REGIONCODE=\"77\"", "REGIONCODE=\"50\"")
                + GarStreetFixture.item(105, "1.999.105")
                + GarStreetFixture.item(106, "1.1.106")
                + GarStreetFixture.item(107, "1.108")
                + GarStreetFixture.item(108, "2.108")
                + GarStreetFixture.item(109, "1.bad.109")
                + GarStreetFixture.item(110, "1.110")
                + GarStreetFixture.item(2, "1.2") + "</ITEMS>");

        assertThat(reader.readCandidates(directory)).extracting(StreetRow::streetObjectId).containsExactly(100L);
    }

    @Test
    void rejectsMismatchedSnapshotDates() throws Exception {
        GarStreetFixture.write(directory, 1);
        var files = GarFiles.discoverStreets(directory);
        Files.move(files.hierarchy(), directory.resolve("AS_ADM_HIERARCHY_20260918_fixture.XML"));

        assertThatThrownBy(() -> reader.readCandidates(directory)).isInstanceOf(GarImportException.class)
                .hasMessageContaining("dates differ");
    }

    @Test
    void rejectsMalformedXmlAndExternalEntities() throws Exception {
        GarStreetFixture.write(directory, 1);
        var files = GarFiles.discoverStreets(directory);
        Files.writeString(files.hierarchy(), "<ITEMS>");
        assertThatThrownBy(() -> reader.readCandidates(directory)).isInstanceOf(GarImportException.class);

        Files.writeString(files.hierarchy(), "<!DOCTYPE ITEMS [<!ENTITY external SYSTEM 'file:///missing'>]><ITEMS/>");
        assertThatThrownBy(() -> reader.readCandidates(directory)).isInstanceOf(GarImportException.class);
    }
}

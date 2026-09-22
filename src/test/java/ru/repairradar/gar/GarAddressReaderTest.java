package ru.repairradar.gar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.repairradar.dto.AddressRow;
import ru.repairradar.dto.GarHouse;
import ru.repairradar.dto.GarObject;
import ru.repairradar.exception.GarImportException;
import ru.repairradar.service.GarAddressReader;
import ru.repairradar.utility.AddressFormatter;
import ru.repairradar.utility.GarXmlReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GarAddressReaderTest {

    @TempDir
    Path directory;
    private final GarAddressReader reader = new GarAddressReader(new GarXmlReader(), new AddressFormatter());

    @Test
    void readsCandidatesAndFormatsFullAddress() throws Exception {
        GarFixture.write(directory, 150);
        var rows = reader.readCandidates(directory);
        assertThat(rows).hasSize(150).extracting(AddressRow::houseGuid).doesNotHaveDuplicates();
        assertThat(rows.getFirst().fullAddress()).isEqualTo("г. Москва, ул. Верхняя, д. 1, корп. 2");
        assertThat(rows.getFirst().additionalNumber2()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 99})
    void acceptsSmallOrEmptySource(int count) throws Exception {
        GarFixture.write(directory, count);
        assertThat(reader.readCandidates(directory)).hasSize(count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ISACTIVE", "ISACTUAL"})
    void excludesInactiveHouses(String flag) throws Exception {
        var files = GarFixture.write(directory, 101);
        Files.writeString(files.houses(), Files.readString(files.houses()).replaceFirst(flag + "=\"1\"", flag + "=\"0\""));
        assertThat(reader.readCandidates(directory)).hasSize(100)
                .extracting(AddressRow::houseObjectId).doesNotContain(1000L);
    }

    @Test
    void excludesBrokenChainsAndDeduplicatesGuids() throws Exception {
        var files = GarFixture.write(directory, 3);
        String houses = Files.readString(files.houses()).replace(new UUID(0, 1001).toString(), new UUID(0, 1000).toString());
        Files.writeString(files.houses(), houses);
        String hierarchy = Files.readString(files.hierarchy()).replace("1.2.1002", "1.999.2.1002");
        Files.writeString(files.hierarchy(),
                hierarchy.replace("</ITEMS>", "<ITEM OBJECTID=\"1000\" REGIONCODE=\"77\" ISACTIVE=\"1\" PATH=\"1.2.1000\"/></ITEMS>"));
        assertThat(reader.readCandidates(directory)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"<HOUSES>", "<WRONG/>", "<!DOCTYPE HOUSES [<!ENTITY x SYSTEM 'file:///missing'>]><HOUSES/>"})
    void rejectsBrokenXmlAndDtd(String xml) throws Exception {
        var files = GarFixture.write(directory, 1);
        Files.writeString(files.houses(), xml);
        assertThatThrownBy(() -> reader.readCandidates(directory)).isInstanceOf(GarImportException.class)
                .extracting("code").isEqualTo("GAR_XML_INVALID");
    }

    @Test
    void requiresOneFileOfEachKindAndMatchingDates() throws Exception {
        var files = GarFixture.write(directory, 1);
        Path duplicate = directory.resolve("AS_HOUSES_20260917_duplicate.XML");
        Files.copy(files.houses(), duplicate);
        assertSourceInvalid();
        Files.delete(duplicate);
        Files.move(files.houses(), directory.resolve("AS_HOUSES_20260918_fixture.XML"));
        assertSourceInvalid();
    }

    @Test
    void missingFileFails() throws Exception {
        var files = GarFixture.write(directory, 1);
        Files.delete(files.houses());
        assertSourceInvalid();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.999.2.1000", "1.2.2.1000", "1.2.9999"})
    void excludesIncompleteCyclicAndWrongHousePaths(String path) throws Exception {
        var files = GarFixture.write(directory, 1);
        Files.writeString(files.hierarchy(), Files.readString(files.hierarchy()).replace("1.2.1000", path));
        assertThat(reader.readCandidates(directory)).isEmpty();
    }

    @Test
    void inactiveHierarchyIsNotEligible() throws Exception {
        var files = GarFixture.write(directory, 1);
        Files.writeString(files.hierarchy(), Files.readString(files.hierarchy()).replace("ISACTIVE=\"1\"", "ISACTIVE=\"0\""));
        assertThat(reader.readCandidates(directory)).isEmpty();
    }

    @Test
    void preservesLettersAndUnknownAdditionalType() {
        var house = new GarHouse(1000, new UUID(0, 1000), "12/3А", 2, "2Б", 1, "7", 99);
        assertThat(new AddressFormatter().format(List.of(new GarObject(1, new UUID(0, 1), "Москва", "г.", 1)), house))
                .isEqualTo("г. Москва, д. 12/3А, корп. 2Б, тип доп. номера 99 7");
    }

    private void assertSourceInvalid() {
        assertThatThrownBy(() -> reader.readCandidates(directory)).isInstanceOf(GarImportException.class)
                .extracting("code").isEqualTo("GAR_SOURCE_INVALID");
    }
}
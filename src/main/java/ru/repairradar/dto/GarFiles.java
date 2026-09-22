package ru.repairradar.dto;

import ru.repairradar.exception.GarImportException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record GarFiles(Path objects, Path houses, Path hierarchy) {

    private static final Pattern OBJECTS = Pattern.compile("^AS_ADDR_OBJ_(\\d{8})_.+\\.XML$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOUSES = Pattern.compile("^AS_HOUSES_(\\d{8})_.+\\.XML$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HIERARCHY = Pattern.compile("^AS_ADM_HIERARCHY_(\\d{8})_.+\\.XML$", Pattern.CASE_INSENSITIVE);

    public static GarFiles discover(Path directory) {
        return discover(directory, true);
    }

    public static GarFiles discoverStreets(Path directory) {
        return discover(directory, false);
    }

    private static GarFiles discover(Path directory, boolean requireHouses) {
        try (var stream = Files.list(directory)) {
            var paths = stream.toList();
            var dates = new ArrayList<String>();
            Path objects = find(paths, "AS_ADDR_OBJ", OBJECTS, dates);
            Path houses = requireHouses ? find(paths, "AS_HOUSES", HOUSES, dates) : null;
            Path hierarchy = find(paths, "AS_ADM_HIERARCHY", HIERARCHY, dates);
            if (dates.stream().distinct().count() != 1) {
                throw invalid("XML dates differ");
            }
            return new GarFiles(objects, houses, hierarchy);
        } catch (IOException e) {
            throw new GarImportException("GAR_SOURCE_INVALID", "Cannot list GAR directory", e);
        }
    }

    private static Path find(List<Path> paths, String prefix, Pattern pattern, List<String> dates) {
        List<Path> matches = new ArrayList<>();
        String date = null;
        for (Path path : paths) {
            Matcher matcher = pattern.matcher(path.getFileName().toString());
            if (!matcher.matches()) {
                continue;
            }
            matches.add(path);
            date = matcher.group(1);
        }
        if (matches.size() != 1) {
            throw invalid("Expected one " + prefix + " XML, found " + matches.size());
        }
        Path path = matches.getFirst();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw invalid("Unreadable " + prefix);
        }
        dates.add(date);
        return path;
    }

    private static GarImportException invalid(String message) {
        return new GarImportException("GAR_SOURCE_INVALID", message);
    }
}

package ru.repairradar.utility;

import org.springframework.stereotype.Component;
import ru.repairradar.exception.GarImportException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class GarXmlReader {

    public void read(Path file, String root, String element, Consumer<Map<String, String>> consumer) {
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External entities disabled");
        });
        try (var input = new BufferedInputStream(Files.newInputStream(file))) {
            var reader = factory.createXMLStreamReader(input);
            try {
                boolean rootSeen = false;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD) {
                        throw new XMLStreamException("DTD disabled");
                    }
                    if (event != XMLStreamConstants.START_ELEMENT) {
                        continue;
                    }
                    if (!rootSeen) {
                        if (!root.equals(reader.getLocalName())) {
                            throw new XMLStreamException("Unexpected root");
                        }
                        rootSeen = true;
                        continue;
                    }
                    if (!element.equals(reader.getLocalName())) {
                        continue;
                    }
                    Map<String, String> attributes = new HashMap<>();
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                    }
                    consumer.accept(attributes);
                }
                if (!rootSeen) {
                    throw new XMLStreamException("Missing root");
                }
            } finally {
                reader.close();
            }
        } catch (IOException | XMLStreamException e) {
            throw new GarImportException("GAR_XML_INVALID", "Cannot parse " + file.getFileName(), e);
        }
    }
}

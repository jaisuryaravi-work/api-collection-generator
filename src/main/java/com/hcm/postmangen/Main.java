package com.hcm.postmangen;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hcm.postmangen.generator.CollectionGenerator;
import com.hcm.postmangen.input.TextInventoryParser;
import com.hcm.postmangen.model.ApiEntry;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -jar postman-collection-generator.jar <input-file> <output-json> [collection-name]");
            System.out.println("  <input-file> can be a .json inventory or a .txt block-format inventory (see README)");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];
        String collectionName = args.length >= 3 ? args[2] : "Global HCM - All APIs";

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

        List<ApiEntry> entries;
        if (inputPath.toLowerCase().endsWith(".json")) {
            entries = mapper.readValue(
                    new File(inputPath),
                    mapper.getTypeFactory().constructCollectionType(List.class, ApiEntry.class)
            );
        } else {
            entries = new TextInventoryParser(mapper).parse(Paths.get(inputPath));
        }

        System.out.println("Loaded " + entries.size() + " API entries from " + inputPath);

        CollectionGenerator generator = new CollectionGenerator(mapper);
        ObjectNode collection = generator.generate(entries, collectionName);

        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, collection);

        System.out.println("Postman collection written to " + outputPath);
    }
}

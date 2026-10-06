package com.hcm.postmangen.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcm.postmangen.model.ApiEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a plain-text API inventory where each entry is a block separated
 * by a line containing only "===". This exists so a curl command can be
 * pasted exactly as copied (multi-line, unescaped quotes, backslash line
 * continuations) without needing to hand-escape it into a JSON string.
 *
 * Block format:
 *
 *   ===
 *   path: Core HCM > Employee Profile Management > View Personal Details
 *   name: View Personal Info [S1-WIP-R140226]
 *   curl:
 *   curl --location 'https://api.hcm.com/hcm/employee/personal-info' \
 *   --header 'Content-Type: application/json' \
 *   --data-raw '{
 *       "employeeId": 123
 *   }'
 *   sampleResponse:
 *   { "employeeId": 123, "firstName": "John" }
 *   ===
 *
 * - path: segments separated by ">" (optional - defaults to Uncategorized)
 * - name: single line
 * - curl: everything after this line, up to the next recognized marker or
 *   the next "===", is taken verbatim as the curl command
 * - sampleResponse: everything after this line, up to the next "===", is
 *   parsed as JSON
 */
public class TextInventoryParser {

    private final ObjectMapper mapper;

    public TextInventoryParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<ApiEntry> parse(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<ApiEntry> entries = new ArrayList<>();

        int i = 0;
        while (i < lines.size() && !isSeparator(lines.get(i))) {
            i++; // skip anything before the first "===" (e.g. blank lines, comments)
        }

        while (i < lines.size()) {
            if (isSeparator(lines.get(i))) {
                i++;
            }
            List<String> block = new ArrayList<>();
            while (i < lines.size() && !isSeparator(lines.get(i))) {
                block.add(lines.get(i));
                i++;
            }
            if (!block.isEmpty()) {
                ApiEntry entry = parseBlock(block);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        return entries;
    }

    private boolean isSeparator(String line) {
        return line.trim().equals("===");
    }

    private ApiEntry parseBlock(List<String> block) {
        ApiEntry entry = new ApiEntry();
        List<String> curlLines = null;
        List<String> responseLines = null;
        List<String> testLines = null;
        List<String> preLines = null;
        String mode = null; // "curl", "response", "test", or "prerequest"

        // Tracks whether we're still inside an open quote from a previous
        // line of the curl command (e.g. a multi-line --data-raw '{ ... }'
        // body). While that's true, a line is never treated as a marker -
        // even if it looks like one - because it's actually still part of
        // the curl command's body content.
        boolean[] quoteState = {false, false}; // [inSingleQuote, inDoubleQuote]

        for (String line : block) {
            boolean insideOpenQuote = "curl".equals(mode) && (quoteState[0] || quoteState[1]);

            String trimmedLower = line.trim().toLowerCase();
            // Strip quotes too, so a JSON-style "sampleResponse": {...} line
            // is still recognized as the marker, not swallowed as curl text.
            String normalized = trimmedLower.replace("\"", "").replace("'", "").replace(" ", "");

            if (!insideOpenQuote && normalized.startsWith("path:")) {
                mode = null;
                String value = line.substring(line.indexOf(':') + 1).trim();
                List<String> segments = new ArrayList<>();
                for (String seg : value.split(">")) {
                    String s = seg.trim();
                    if (!s.isEmpty()) {
                        segments.add(s);
                    }
                }
                entry.setPath(segments);
                continue;
            }

            if (!insideOpenQuote && normalized.startsWith("name:")) {
                mode = null;
                entry.setName(line.substring(line.indexOf(':') + 1).trim());
                continue;
            }

            if (!insideOpenQuote && normalized.startsWith("curl:")) {
                mode = "curl";
                curlLines = new ArrayList<>();
                quoteState[0] = false;
                quoteState[1] = false;
                String rest = line.substring(line.indexOf(':') + 1);
                if (!rest.trim().isEmpty()) {
                    curlLines.add(rest);
                    scanQuotes(rest, quoteState);
                }
                continue;
            }

            if (!insideOpenQuote && (normalized.startsWith("sampleresponse:") || normalized.startsWith("response:"))) {
                mode = "response";
                responseLines = new ArrayList<>();
                String rest = line.substring(line.indexOf(':') + 1);
                if (!rest.trim().isEmpty()) {
                    responseLines.add(rest);
                }
                continue;
            }

            if (!insideOpenQuote && (normalized.startsWith("prerequest:") || normalized.startsWith("prescript:"))) {
                mode = "prerequest";
                preLines = new ArrayList<>();
                String rest = line.substring(line.indexOf(':') + 1);
                if (!rest.trim().isEmpty()) {
                    preLines.add(rest);
                }
                continue;
            }

            if (!insideOpenQuote && (normalized.startsWith("test:") || normalized.startsWith("script:"))) {
                mode = "test";
                testLines = new ArrayList<>();
                String rest = line.substring(line.indexOf(':') + 1);
                if (!rest.trim().isEmpty()) {
                    testLines.add(rest);
                }
                continue;
            }

            if ("curl".equals(mode) && curlLines != null) {
                curlLines.add(line);
                scanQuotes(line, quoteState);
            } else if ("response".equals(mode) && responseLines != null) {
                responseLines.add(line);
            } else if ("test".equals(mode) && testLines != null) {
                testLines.add(line);
            } else if ("prerequest".equals(mode) && preLines != null) {
                preLines.add(line);
            }
        }

        if (entry.getName() == null || curlLines == null || curlLines.isEmpty()) {
            System.err.println("Skipping a block - missing 'name:' or 'curl:' content: "
                    + (entry.getName() != null ? entry.getName() : block.get(0)));
            return null;
        }

        entry.setCurl(String.join("\n", curlLines).trim());

        if (responseLines != null && !responseLines.isEmpty()) {
            String json = String.join("\n", responseLines).trim();
            try {
                entry.setSampleResponse(mapper.readTree(json));
            } catch (IOException e) {
                System.err.println("Warning: could not parse sampleResponse for '"
                        + entry.getName() + "' - " + e.getMessage());
            }
        }

        if (testLines != null && !testLines.isEmpty()) {
            entry.setTestScript(testLines);
        }

        if (preLines != null && !preLines.isEmpty()) {
            entry.setPreRequestScript(preLines);
        }

        return entry;
    }

    /**
     * Updates single/double quote parity as text is appended to the curl
     * command, mirroring CurlParser's own tokenizer rules: inside a single
     * quote, a double quote is literal (and vice versa).
     */
    private static void scanQuotes(String text, boolean[] quoteState) {
        boolean inSingle = quoteState[0];
        boolean inDouble = quoteState[1];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            }
        }
        quoteState[0] = inSingle;
        quoteState[1] = inDouble;
    }
}
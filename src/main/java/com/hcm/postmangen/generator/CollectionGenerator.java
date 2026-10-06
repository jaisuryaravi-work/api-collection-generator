package com.hcm.postmangen.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hcm.postmangen.curl.CurlParser;
import com.hcm.postmangen.curl.FormField;
import com.hcm.postmangen.curl.ParsedCurl;
import com.hcm.postmangen.model.ApiEntry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a Postman Collection v2.1 JSON tree (folders + requests + sample
 * response examples) from a list of API entries.
 */
public class CollectionGenerator {

    private final ObjectMapper mapper;

    public CollectionGenerator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode generate(List<ApiEntry> entries, String collectionName) {
        ArrayNode rootItems = mapper.createArrayNode();
        Map<String, ArrayNode> folderIndex = new LinkedHashMap<>();
        int skipped = 0;
        int folderCount = 0;

        for (ApiEntry entry : entries) {
            ParsedCurl parsedCurl;
            try {
                parsedCurl = CurlParser.parse(entry.getCurl());
            } catch (Exception e) {
                System.err.println("Skipping '" + entry.getName() + "' - failed to parse curl: " + e.getMessage());
                skipped++;
                continue;
            }

            if (parsedCurl.getUrl() == null) {
                System.err.println("Skipping '" + entry.getName() + "' - no URL found in curl command");
                skipped++;
                continue;
            }

            List<String> path = (entry.getPath() == null || entry.getPath().isEmpty())
                    ? Collections.singletonList("Uncategorized") : entry.getPath();

            ArrayNode leafFolderItems = rootItems;
            StringBuilder key = new StringBuilder();
            for (String segment : path) {
                key.append(" > ").append(segment);
                String folderKey = key.toString();

                ArrayNode existing = folderIndex.get(folderKey);
                if (existing == null) {
                    ObjectNode folderNode = mapper.createObjectNode();
                    folderNode.put("name", segment);
                    ArrayNode childItems = mapper.createArrayNode();
                    folderNode.set("item", childItems);

                    leafFolderItems.add(folderNode);
                    folderIndex.put(folderKey, childItems);
                    folderCount++;
                    existing = childItems;
                }
                leafFolderItems = existing;
            }

            ObjectNode requestNode = buildRequestNode(parsedCurl);
            ObjectNode itemNode = buildItemNode(entry, requestNode);
            leafFolderItems.add(itemNode);
        }

        ObjectNode collection = mapper.createObjectNode();
        ObjectNode info = mapper.createObjectNode();
        info.put("name", collectionName);
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        collection.set("info", info);
        collection.set("item", rootItems);

        System.out.println("Generated " + (entries.size() - skipped) + " request(s) across "
                + folderCount + " folder(s)" + (skipped > 0 ? " (" + skipped + " skipped)" : ""));

        return collection;
    }

    private ObjectNode buildRequestNode(ParsedCurl parsedCurl) {
        ObjectNode request = mapper.createObjectNode();
        request.put("method", parsedCurl.getMethod());

        ArrayNode headerArray = mapper.createArrayNode();
        for (Map.Entry<String, String> h : parsedCurl.getHeaders().entrySet()) {
            ObjectNode headerNode = mapper.createObjectNode();
            headerNode.put("key", h.getKey());
            headerNode.put("value", h.getValue());
            headerNode.put("type", "text");
            headerArray.add(headerNode);
        }
        request.set("header", headerArray);

        if (!parsedCurl.getFormData().isEmpty()) {
            ObjectNode body = mapper.createObjectNode();
            body.put("mode", "formdata");
            ArrayNode formArray = mapper.createArrayNode();
            for (FormField field : parsedCurl.getFormData()) {
                ObjectNode fieldNode = mapper.createObjectNode();
                fieldNode.put("key", field.getKey());
                if (field.isFile()) {
                    fieldNode.put("type", "file");
                    fieldNode.put("src", field.getValue());
                } else {
                    fieldNode.put("type", "text");
                    fieldNode.put("value", field.getValue());
                }
                formArray.add(fieldNode);
            }
            body.set("formdata", formArray);
            request.set("body", body);
        } else if (!parsedCurl.getUrlEncodedData().isEmpty()) {
            ObjectNode body = mapper.createObjectNode();
            body.put("mode", "urlencoded");
            ArrayNode urlEncodedArray = mapper.createArrayNode();
            for (FormField field : parsedCurl.getUrlEncodedData()) {
                ObjectNode fieldNode = mapper.createObjectNode();
                fieldNode.put("key", field.getKey());
                fieldNode.put("value", field.getValue());
                fieldNode.put("type", "text");
                urlEncodedArray.add(fieldNode);
            }
            body.set("urlencoded", urlEncodedArray);
            request.set("body", body);
        } else if (parsedCurl.getBody() != null && !parsedCurl.getBody().isEmpty()) {
            ObjectNode body = mapper.createObjectNode();
            body.put("mode", "raw");
            body.put("raw", parsedCurl.getBody());
            ObjectNode options = mapper.createObjectNode();
            ObjectNode rawOptions = mapper.createObjectNode();
            rawOptions.put("language", looksLikeJson(parsedCurl.getBody()) ? "json" : "text");
            options.set("raw", rawOptions);
            body.set("options", options);
            request.set("body", body);
        }

        request.set("url", buildUrlNode(parsedCurl.getUrl()));

        ObjectNode authNode = buildAuthNode(parsedCurl);
        if (authNode != null) {
            request.set("auth", authNode);
        }

        return request;
    }

    private ObjectNode buildAuthNode(ParsedCurl parsedCurl) {
        String authType = parsedCurl.getAuthType();
        if (authType == null) {
            return null;
        }

        ObjectNode authNode = mapper.createObjectNode();

        if ("bearer".equals(authType)) {
            authNode.put("type", "bearer");
            ArrayNode bearerArray = mapper.createArrayNode();
            ObjectNode tokenField = mapper.createObjectNode();
            tokenField.put("key", "token");
            tokenField.put("value", parsedCurl.getBearerToken());
            tokenField.put("type", "string");
            bearerArray.add(tokenField);
            authNode.set("bearer", bearerArray);
            return authNode;
        }

        if ("basic".equals(authType)) {
            authNode.put("type", "basic");
            ArrayNode basicArray = mapper.createArrayNode();

            ObjectNode usernameField = mapper.createObjectNode();
            usernameField.put("key", "username");
            usernameField.put("value", parsedCurl.getBasicUsername());
            usernameField.put("type", "string");
            basicArray.add(usernameField);

            ObjectNode passwordField = mapper.createObjectNode();
            passwordField.put("key", "password");
            passwordField.put("value", parsedCurl.getBasicPassword());
            passwordField.put("type", "string");
            basicArray.add(passwordField);

            authNode.set("basic", basicArray);
            return authNode;
        }

        return null;
    }

    // Matches a leading scheme, e.g. "https://" - deliberately does NOT
    // require the scheme to be a known one, so custom schemes still work.
    private static final Pattern SCHEME_PATTERN = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*)://");

    /**
     * Builds Postman's url object (raw + protocol/host/path/query) without
     * relying on java.net.URI, because URI.create() rejects Postman
     * variables like "{{APIGateway}}/login" (curly braces aren't valid URI
     * characters) and throws instead of parsing. This manual version splits
     * on the same delimiters ("://" for protocol, first "/" for host vs
     * path, "?" for query, "." within the host, "/" within the path) that
     * Postman's own collection format uses, so a variable-only host like
     * "{{APIGateway}}" ends up as a single host segment - matching what
     * Postman itself produces when you build the request by hand.
     */
    private ObjectNode buildUrlNode(String rawUrl) {
        ObjectNode urlNode = mapper.createObjectNode();
        urlNode.put("raw", rawUrl);

        String remaining = rawUrl;

        Matcher schemeMatcher = SCHEME_PATTERN.matcher(remaining);
        if (schemeMatcher.find()) {
            urlNode.put("protocol", schemeMatcher.group(1));
            remaining = remaining.substring(schemeMatcher.end());
        }

        String query = null;
        int queryIdx = remaining.indexOf('?');
        if (queryIdx >= 0) {
            query = remaining.substring(queryIdx + 1);
            remaining = remaining.substring(0, queryIdx);
        }

        String hostPart;
        String pathPart;
        int slashIdx = remaining.indexOf('/');
        if (slashIdx >= 0) {
            hostPart = remaining.substring(0, slashIdx);
            pathPart = remaining.substring(slashIdx + 1);
        } else {
            hostPart = remaining;
            pathPart = "";
        }

        if (!hostPart.isEmpty()) {
            ArrayNode hostArray = mapper.createArrayNode();
            for (String part : hostPart.split("\\.")) {
                hostArray.add(part);
            }
            urlNode.set("host", hostArray);
        }

        if (!pathPart.isEmpty()) {
            ArrayNode pathArray = mapper.createArrayNode();
            for (String part : pathPart.split("/")) {
                if (!part.isEmpty()) {
                    pathArray.add(part);
                }
            }
            urlNode.set("path", pathArray);
        }

        if (query != null && !query.isEmpty()) {
            ArrayNode queryArray = mapper.createArrayNode();
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                ObjectNode queryNode = mapper.createObjectNode();
                queryNode.put("key", kv[0]);
                queryNode.put("value", kv.length > 1 ? kv[1] : "");
                queryArray.add(queryNode);
            }
            urlNode.set("query", queryArray);
        }

        return urlNode;
    }

    private ObjectNode buildEventNode(String listenType, List<String> lines) {
        ObjectNode eventNode = mapper.createObjectNode();
        eventNode.put("listen", listenType);

        ObjectNode scriptNode = mapper.createObjectNode();
        ArrayNode execArray = mapper.createArrayNode();
        for (String line : lines) {
            execArray.add(line);
        }
        scriptNode.set("exec", execArray);
        scriptNode.put("type", "text/javascript");
        scriptNode.set("packages", mapper.createObjectNode());
        scriptNode.set("requests", mapper.createObjectNode());

        eventNode.set("script", scriptNode);
        return eventNode;
    }

    private ObjectNode buildItemNode(ApiEntry entry, ObjectNode requestNode) {
        ObjectNode item = mapper.createObjectNode();
        item.put("name", entry.getName());
        item.set("request", requestNode);

        ArrayNode eventArray = mapper.createArrayNode();
        if (entry.getPreRequestScript() != null && !entry.getPreRequestScript().isEmpty()) {
            eventArray.add(buildEventNode("prerequest", entry.getPreRequestScript()));
        }
        if (entry.getTestScript() != null && !entry.getTestScript().isEmpty()) {
            eventArray.add(buildEventNode("test", entry.getTestScript()));
        }
        if (eventArray.size() > 0) {
            item.set("event", eventArray);
        }

        ArrayNode responses = mapper.createArrayNode();

        JsonNode sample = entry.getSampleResponse();
        if (sample != null && !sample.isNull()) {
            ObjectNode response = mapper.createObjectNode();
            response.put("name", "Sample Response");
            response.set("originalRequest", requestNode.deepCopy());
            response.put("status", "OK");
            response.put("code", 200);
            response.put("_postman_previewlanguage", "json");

            ArrayNode responseHeaders = mapper.createArrayNode();
            ObjectNode contentTypeHeader = mapper.createObjectNode();
            contentTypeHeader.put("key", "Content-Type");
            contentTypeHeader.put("value", "application/json");
            responseHeaders.add(contentTypeHeader);
            response.set("header", responseHeaders);

            String bodyText;
            try {
                bodyText = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sample);
            } catch (Exception e) {
                bodyText = sample.toString();
            }
            response.put("body", bodyText);

            responses.add(response);
        }
        // If no sampleResponse was given, "response" stays an empty array -
        // no placeholder example is created.
        item.set("response", responses);

        return item;
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
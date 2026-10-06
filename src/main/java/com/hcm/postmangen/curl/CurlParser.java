package com.hcm.postmangen.curl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Parses a curl command string into method, URL, headers and body.
 * Handles the common flags used in real-world curl commands (copied from
 * browser devtools, Swagger UI, or written by hand). Not a full curl
 * implementation - unsupported flags are simply ignored.
 */
public class CurlParser {

    public static ParsedCurl parse(String curlCommand) {
        ParsedCurl parsed = new ParsedCurl();

        // Join line-continuations ("\" at end of line) so multi-line curl
        // commands copied from docs still tokenize as one command.
        String normalized = curlCommand.replaceAll("\\\\\\r?\\n", " ");

        List<String> tokens = tokenize(normalized);
        boolean methodExplicitlySet = false;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if (token.equals("curl")) {
                continue;
            }

            switch (token) {
                case "-X":
                case "--request":
                    parsed.setMethod(tokens.get(++i).toUpperCase());
                    methodExplicitlySet = true;
                    break;

                case "-H":
                case "--header":
                    String headerToken = tokens.get(++i);
                    int colonIdx = headerToken.indexOf(':');
                    if (colonIdx > 0) {
                        String key = headerToken.substring(0, colonIdx).trim();
                        String value = headerToken.substring(colonIdx + 1).trim();

                        if (key.equalsIgnoreCase("authorization") && tryCaptureAuth(parsed, value)) {
                            // Captured as structured auth (Bearer/Basic) - don't
                            // also add it as a plain header, or Postman will
                            // show it twice (Auth tab + Headers tab).
                            break;
                        }

                        parsed.getHeaders().put(key, value);
                    }
                    break;

                case "-d":
                case "--data":
                case "--data-raw":
                case "--data-binary":
                    parsed.setBody(tokens.get(++i));
                    if (!methodExplicitlySet) {
                        parsed.setMethod("POST");
                    }
                    break;

                case "--data-urlencode":
                    String pairToken = tokens.get(++i);
                    int eqIdx = pairToken.indexOf('=');
                    String encKey = eqIdx >= 0 ? pairToken.substring(0, eqIdx) : pairToken;
                    String encValue = eqIdx >= 0 ? pairToken.substring(eqIdx + 1) : "";
                    parsed.getUrlEncodedData().add(new FormField(encKey, encValue, false));
                    if (!methodExplicitlySet) {
                        parsed.setMethod("POST");
                    }
                    break;

                case "-F":
                case "--form":
                    parsed.getFormData().add(parseFormField(tokens.get(++i)));
                    if (!methodExplicitlySet) {
                        parsed.setMethod("POST");
                    }
                    break;

                case "--url":
                    parsed.setUrl(tokens.get(++i));
                    break;

                case "-u":
                case "--user":
                    String creds = tokens.get(++i);
                    int userColon = creds.indexOf(':');
                    if (userColon >= 0) {
                        parsed.setAuthType("basic");
                        parsed.setBasicUsername(creds.substring(0, userColon));
                        parsed.setBasicPassword(creds.substring(userColon + 1));
                    } else {
                        parsed.setAuthType("basic");
                        parsed.setBasicUsername(creds);
                        parsed.setBasicPassword("");
                    }
                    break;

                case "-b":
                case "--cookie":
                    parsed.getHeaders().put("Cookie", tokens.get(++i));
                    break;

                case "-A":
                case "--user-agent":
                    parsed.getHeaders().put("User-Agent", tokens.get(++i));
                    break;

                default:
                    if (!token.startsWith("-")) {
                        // Any bare positional argument is the URL - this covers
                        // plain https://... URLs as well as URLs built from
                        // Postman-style variables, e.g. '{{APIGateway}}/login'.
                        parsed.setUrl(token);
                    }
                    // Any other flag (-s, -k, -i, -L, --compressed, -v, etc.)
                    // takes no argument in normal usage, so it's safely ignored.
                    break;
            }
        }

        return parsed;
    }

    /**
     * Attempts to interpret an Authorization header value as Bearer or Basic
     * auth and records it on the ParsedCurl as structured auth. Returns true
     * if it was recognized (caller should then skip adding it as a plain
     * header); returns false for any other scheme (Digest, ApiKey, a custom
     * scheme, etc.) so it falls back to being a normal header.
     */
    private static boolean tryCaptureAuth(ParsedCurl parsed, String headerValue) {
        if (headerValue.length() > 7 && headerValue.regionMatches(true, 0, "Bearer ", 0, 7)) {
            parsed.setAuthType("bearer");
            parsed.setBearerToken(headerValue.substring(7).trim());
            return true;
        }

        if (headerValue.length() > 6 && headerValue.regionMatches(true, 0, "Basic ", 0, 6)) {
            String encoded = headerValue.substring(6).trim();
            try {
                String decoded = new String(Base64.getDecoder().decode(encoded));
                int colon = decoded.indexOf(':');
                if (colon >= 0) {
                    parsed.setAuthType("basic");
                    parsed.setBasicUsername(decoded.substring(0, colon));
                    parsed.setBasicPassword(decoded.substring(colon + 1));
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Not valid Base64 - fall through and keep it as a plain header.
            }
        }

        return false;
    }

    /**
     * Parses one --form / -F token, e.g. 'trace="true"' or
     * 'data.files[0].file=@"/path/to/file.jpg"'. A value starting with "@"
     * is a file upload (the rest is the local file path); curl also allows
     * a ";type=..." suffix after the file path, which is stripped off here
     * since Postman infers content-type from the file itself. Values may be
     * wrapped in double quotes with backslash-escaped inner quotes (as curl
     * itself expects) - both are unwrapped here to get the real value.
     */
    private static FormField parseFormField(String token) {
        int eq = token.indexOf('=');
        if (eq < 0) {
            // No "=" - treat the whole token as a valueless key (rare/malformed).
            return new FormField(token, "", false);
        }

        String key = token.substring(0, eq);
        String rawValue = token.substring(eq + 1);

        boolean isFile = rawValue.startsWith("@");
        if (isFile) {
            rawValue = rawValue.substring(1);
            int semicolon = rawValue.indexOf(';');
            if (semicolon >= 0) {
                rawValue = rawValue.substring(0, semicolon);
            }
        }

        String value = stripQuotesAndUnescape(rawValue);
        return new FormField(key, value, isFile);
    }

    private static String stripQuotesAndUnescape(String raw) {
        String result = raw;
        if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        return result.replace("\\\"", "\"");
    }

    /**
     * Splits a command string into tokens, respecting single and double
     * quoted sections (so headers/bodies containing spaces stay intact).
     */
    private static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }
            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }
            if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
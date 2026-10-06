# Postman Collection Generator (Java + Maven)

Generates a fully organized Postman Collection (v2.1) — folders, requests,
and sample response examples — from a list of curl commands. Built for
teams maintaining large collections (600+ APIs) where doing this by hand
in the Postman UI isn't practical.

## How it works

1. You maintain one JSON file (`api-inventory.json`) listing every API:
   which module/folder it belongs to, its curl command, and an example
   response.
2. Running the tool parses each curl command (method, URL, headers, body)
   and generates a complete Postman collection JSON.
3. Import that JSON file into Postman once — every folder, request, and
   example appears fully formed.

Regenerate the collection whenever APIs change, instead of hand-editing
it in Postman.

## Project structure

```
postman-collection-generator/
├── pom.xml
├── README.md
├── input/
│   ├── api-inventory-sample.txt     (recommended: paste raw curl commands)
│   └── api-inventory-sample.json    (alternative: programmatic JSON input)
├── output/                              (generated collection lands here)
└── src/main/java/com/hcm/postmangen/
    ├── Main.java
    ├── model/ApiEntry.java
    ├── input/TextInventoryParser.java   (parses the .txt block format)
    ├── curl/CurlParser.java
    ├── curl/ParsedCurl.java
    └── generator/CollectionGenerator.java
```

## Build

Requires Java 11+ and Maven (with internet access to Maven Central, for
the Jackson dependency).

```bash
mvn clean package
```

This produces `target/postman-collection-generator.jar` — a fat/shaded
jar, so no separate dependency jars are needed to run it.

## Run

```bash
java -jar target/postman-collection-generator.jar input/api-inventory-sample.json output/global-hcm-collection.json "Global HCM - All APIs"
```

Arguments:
1. Path to your input JSON (list of API entries)
2. Path to write the generated Postman collection
3. (Optional) Collection name — defaults to `Global HCM - All APIs`

Then in Postman: **Import → File → select `output/global-hcm-collection.json`**.

## Input format

Two formats are supported, chosen automatically by file extension.

### Option A: `.txt` block format (recommended for pasting real curl commands)

This is the one to use when you have actual curl commands copied from
Postman, browser devtools, or Swagger — paste them exactly as-is, with
real line breaks and unescaped quotes. No JSON escaping required.

```
===
path: Core HCM > Employee Profile Management > View Personal Details
name: View Personal Info [S1-WIP-R140226]
curl:
curl --location 'https://api.hcm.com/hcm/employee/personal-info' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer {{token}}' \
--data-raw '{
    "employeeId": 123
}'
sampleResponse:
{ "employeeId": 123, "firstName": "John", "lastName": "Doe" }
===
path: Authentication
name: Login
curl:
curl --location 'https://api.hcm.com/auth/login' \
--header 'Content-Type: application/json' \
--data-raw '{"username":"jdoe","password":"secret"}'
sampleResponse:
{ "token": "eyJhbGciOi...", "expiresIn": 3600 }
===
```

Rules:
- `===` on its own line separates entries.
- `path:` — folder breadcrumb, segments separated by `>`. Optional; omit
  for an `Uncategorized` top-level folder.
- `name:` — the request's display name (one line). Sprint/status tags
  like `[S1-WIP-R140226]` are just plain text here.
- `curl:` — everything after this line, up to the next marker or `===`,
  is taken verbatim as the curl command. Paste multi-line curl commands
  (with trailing `\` continuations) exactly as copied.
- `sampleResponse:` — everything after this line, up to `===`, is parsed
  as JSON and attached as a saved 200 OK example.

Run it the same way, just point at the `.txt` file:

```bash
java -jar target/postman-collection-generator.jar input/api-inventory-sample.txt output/global-hcm-collection.json "01 - GHCM API"
```

### Option B: `.json` format

```json
[
  {
    "path": ["Core HCM", "Employee Profile Management", "View Personal Details"],
    "name": "View Personal Info [S1-WIP-R140226]",
    "curl": "curl -X POST https://api.hcm.com/hcm/employee/personal-info -H 'Content-Type: application/json' -H 'Authorization: Bearer {{token}}' -d '{\"employeeId\":123}'",
    "sampleResponse": { "employeeId": 123, "firstName": "John", "lastName": "Doe" }
  }
]
```

Same fields as above (`path`, `name`, `curl`, `sampleResponse`), but
since it's JSON, the `curl` value must be a single-line string with
internal quotes escaped as `\"`. Fine for API-generated inventories;
tedious for pasting curl by hand — use the `.txt` format for that.

Shared behavior:
- `curl` supports `-X`/`--request`, `-H`/`--header`,
  `-d`/`--data`/`--data-raw`, `--url`/a bare URL (including Postman
  variables like `{{APIGateway}}/login`), `-u`/`--user` (Basic auth),
  `-b`/`--cookie`, `-A`/`--user-agent`, and `--location` (ignored, since
  it takes no value). Method defaults to GET, or POST if a body is
  present without an explicit `-X`.
- An `Authorization: Bearer <token>` or `Authorization: Basic <base64>`
  header is automatically pulled out and written into Postman's
  structured Auth field, so the Auth tab shows "Bearer Token" (or "Basic
  Auth") with the value filled in — not just a raw header, and not
  "Inherit auth from parent". Any other Authorization scheme (Digest,
  a custom API key header, etc.) is left as a plain header.
- JSON property names are matched case-insensitively (`sampleResponse`,
  `SampleResponse`, etc. all work), and in the `.txt` format the
  `sampleResponse:` / `sample response:` / `response:` markers are all
  accepted the same way.

## Scaling to 600+ APIs

- If you're pasting curl commands you already have, use the `.txt`
  format and just keep appending `===` blocks — no escaping to worry
  about, no risk of a stray quote breaking the whole file.
- If you're generating the inventory from code (DB table, Swagger specs,
  route definitions), the `.json` format is easier to emit programmatically.
- Folders and requests are derived from the `path` and `name` fields —
  reorganizing later means editing the inventory and re-running the tool,
  not dragging things around in the Postman UI.

## Known limitations

- The curl parser handles common flags but not every possible curl option
  (e.g., multipart `-F` form fields, complex auth flows). If a curl
  command uses something unsupported, that entry is skipped with a
  warning printed to the console — check the run output after each batch.
- Sample responses are static (whatever you put in `sampleResponse`). If
  you want live-captured examples instead, run each curl once and paste
  the real response into the inventory file before generating.

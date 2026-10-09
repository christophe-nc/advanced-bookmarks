# Advanced Bookmarks

Advanced Bookmarks is an XWiki extension for managing personal bookmarks in folders, with a page star and a quick-access panel. The storage component is permission-agnostic; calling server-side services must enforce that users only access their own data and that administrators have the intended additional access.

## Storage

The extension stores one Solr document per user and wiki in the dedicated `advancedbookmarks` core. The document contains the serialized folders and bookmarks, the wiki and user identifiers, and a multivalued page-reference field used to find bookmarks during page renames. Solr's native `_version_` field provides optimistic concurrency control.

The core is initialized by the extension when the XWiki Solr configuration supports automatic core creation. In installations using a remote Solr server, an administrator must create the `advancedbookmarks` core before enabling the extension.

The bookmark core is not rebuilt from the XWiki database. Include its data directory in regular, consistent backups. In the default local setup, this is under the permanent XWiki data directory. For remote Solr, back up the corresponding core using the Solr operator's established backup procedure. Restoring the core requires a backup made with a compatible Solr installation.

## Requirements

- XWiki 18.0.0 or later
- Java 21

## Build and tests

Build the extension and run its unit and component tests with Maven:

```bash
mvn -B clean verify
```

When Maven and Java are not installed locally, the same build can be run with Docker from the project root:

```bash
docker run --rm -v "$PWD":/projet -v "$HOME/.m2":/root/.m2 -w /projet maven:3.9-eclipse-temurin-21 mvn -B clean verify
```

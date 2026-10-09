# Advanced Bookmarks

This project is the local scaffolding for the Advanced Bookmarks XWiki extension.

## Current status

This repository currently contains the project bootstrap for the first development step:

- Java 21 Maven project skeleton
- base package structure
- bookmark business API and minimal unit test
- page and translation resources for the first macro step

> The final target remains an XWiki extension built against the XWiki parent platform, but the public parent artifact is currently unresolved in the external repository chain. This local bootstrap therefore uses a standalone Java 21 Maven setup so the project structure and first step can be validated immediately.

## Roadmap step in progress

The first roadmap item is the macro `{{bookmarks/}}` exposing the bookmark management UI from any page, with a default home page for the extension.

## Build

Use Docker to compile with Java 21:

```bash
docker run --rm -v "$PWD":/projet -v "$HOME/.m2":/root/.m2 -w /projet maven:3.9-eclipse-temurin-21 mvn -B clean test
```

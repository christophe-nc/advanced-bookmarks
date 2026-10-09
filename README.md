# Advanced Bookmarks

Advanced Bookmarks is an XWiki extension for managing personal bookmarks in folders, with a page star and a quick-access panel.

## Installation

Install the **Advanced Bookmarks** extension (`org.xwiki.contrib.advancedbookmarks:advanced-bookmarks-ui`) with the Extension Manager, as a wiki administrator. Its Java part, `advanced-bookmarks-api`, is installed automatically.

The Java part can only be installed at farm level: the Solr core it creates is shared by all the wikis of the farm, and XWiki creates Solr cores from the farm-level components. Installing the extension on the whole farm requires programming rights.

The installing user must be an administrator of the wiki: the star is only displayed when the author of its UI extension has the administration right.

## Usage

- **Star**: a star button is added to the action bar of every page (next to *Edit* and *Create*), for logged-in users. It adds the page to the uncategorized bookmarks, or removes it. It is not displayed on the pages of the extension itself.
- **Panel**: the *Bookmarks* panel (`Panels.MyBookmarks`) lists the bookmarks selected for the panel, or the first ten, with a link to the management page. It is not added to the page layout automatically: add it in *Administration > Look & Feel > Panels*. Deleted pages are struck through; pages the user cannot view are shown without link.

## Modules

| Module | Packaging | Content |
| --- | --- | --- |
| `advanced-bookmarks-api` | JAR | Storage, business logic, rights, script service and translations. |
| `advanced-bookmarks-ui` | XAR | Wiki pages: JSON service, star and panel. |

## Access rules

Bookmarks are private and kept separately for each wiki of a farm. All the checks are made on the server:

- the current user and wiki always come from the XWiki context, never from the browser;
- users read and modify only their own bookmarks; administrators of a wiki can also manage the bookmarks of any existing user in that wiki;
- guests have no bookmarks;
- a page can only be bookmarked by a user who can view it, and live information about a bookmarked page (whether it exists, its URL) is only returned to users who can view it;
- modifications are only accepted in POST requests carrying a valid CSRF token (`form_token` parameter).

The extension does not require programming rights.

## Script service

`$services.advancedbookmarks` returns maps ready to be serialized as JSON. Each result has a `status` key (an HTTP status code) and, on failure, an `error` key: `forbidden`, `invalidToken`, `methodNotAllowed`, `invalid`, `conflict` or `storageError`. Error details are only written to the server logs.

| Method | Description |
| --- | --- |
| `getBookmarks(user)` | Folders and bookmarks of a user (empty for the current user), in display order, with their `version`. |
| `getPanel()` | Bookmarks of the current user selected for the panel, or the first ten. |
| `isBookmarked(page)` | Whether the current user bookmarked a page. |
| `toggle(page)` | Add the page to the uncategorized bookmarks of the current user, or remove it. Write operation. |
| `save(user, json, version)` | Replace all the bookmarks of a user, provided they were not modified since `version` was read; `conflict` otherwise. Write operation. |

The page `AdvancedBookmarks.Code.Service` exposes the same operations over HTTP, with the `get` action and an `operation` parameter (`isBookmarked`, `toggle`, `getBookmarks`, `save`); it returns the result as JSON with the matching HTTP status.

## Data lifecycle

- When a page is renamed or moved within its wiki, the bookmarks pointing to it are updated. A page moved to another wiki is displayed as deleted.
- A deleted page stays in the bookmarks and is displayed as deleted.
- When a user is deleted, their bookmarks are erased in every wiki. When a wiki is deleted, all its bookmarks are erased.

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

Add `-Pquality` to also check the test coverage, as the XWiki continuous integration does.

When Maven and Java are not installed locally, the same build can be run with Docker from the project root:

```bash
docker run --rm -v "$PWD":/projet -v "$HOME/.m2":/root/.m2 -w /projet maven:3.9-eclipse-temurin-21 mvn -B clean verify
```

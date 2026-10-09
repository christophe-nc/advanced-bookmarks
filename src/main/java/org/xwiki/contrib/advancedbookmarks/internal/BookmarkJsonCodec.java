/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.advancedbookmarks.internal;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;

/**
 * Validates bookmark JSON and maintains the Solr page-reference field.
 *
 * @version $Id$
 * @since 1.0
 */
final class BookmarkJsonCodec
{
    static final int MAX_JSON_BYTES = 1_048_576;
    static final int MAX_ITEMS = 10_000;
    static final int MAX_PAGE_REFERENCE_LENGTH = 512;
    private static final ObjectMapper JSON = new ObjectMapper();

    Set<String> getPageReferences(String json) throws BookmarkStoreException
    {
        JsonNode root = parse(json);
        JsonNode bookmarks = root.get("bookmarks");
        if (bookmarks == null || !bookmarks.isArray() || bookmarks.size() > MAX_ITEMS) {
            throw new BookmarkStoreException("Invalid bookmark data.", null);
        }

        Set<String> pages = new LinkedHashSet<>();
        for (JsonNode bookmark : bookmarks) {
            if (!bookmark.isObject()) {
                throw new BookmarkStoreException("Invalid bookmark data.", null);
            }
            JsonNode page = bookmark.get("page");
            if (page == null || !page.isTextual() || page.textValue().isBlank()
                || page.textValue().length() > MAX_PAGE_REFERENCE_LENGTH) {
                throw new BookmarkStoreException("Invalid bookmark data.", null);
            }
            pages.add(page.textValue());
        }
        return pages;
    }

    String replacePage(String json, String oldPage, String newPage) throws BookmarkStoreException
    {
        JsonNode root = parse(json);
        JsonNode bookmarks = root.get("bookmarks");
        if (bookmarks == null || !bookmarks.isArray() || bookmarks.size() > MAX_ITEMS) {
            throw new BookmarkStoreException("Invalid bookmark data.", null);
        }

        for (JsonNode bookmark : bookmarks) {
            if (!bookmark.isObject()) {
                throw new BookmarkStoreException("Invalid bookmark data.", null);
            }
            JsonNode page = bookmark.get("page");
            if (page != null && page.isTextual() && page.textValue().equals(oldPage)) {
                ((ObjectNode) bookmark).put("page", newPage);
            }
        }

        try {
            return JSON.writeValueAsString(root);
        } catch (Exception e) {
            throw new BookmarkStoreException("Unable to serialize bookmark data.", e);
        }
    }

    private JsonNode parse(String json) throws BookmarkStoreException
    {
        if (json == null || json.isBlank() || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new BookmarkStoreException("Invalid bookmark data.", null);
        }
        try {
            JsonNode root = JSON.readTree(json);
            if (root == null || !root.isObject()) {
                throw new BookmarkStoreException("Invalid bookmark data.", null);
            }
            JsonNode folders = root.get("folders");
            JsonNode bookmarks = root.get("bookmarks");
            if (folders == null || !folders.isArray() || folders.size() > MAX_ITEMS
                || bookmarks == null || !bookmarks.isArray() || bookmarks.size() > MAX_ITEMS) {
                throw new BookmarkStoreException("Invalid bookmark data.", null);
            }
            return root;
        } catch (BookmarkStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new BookmarkStoreException("Invalid bookmark data.", e);
        }
    }
}

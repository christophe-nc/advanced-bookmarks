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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkFolder;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.contrib.advancedbookmarks.BookmarkTooLargeException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests bookmark JSON validation and page-reference extraction.
 *
 * @version $Id$
 */
class BookmarkJsonCodecTest
{
    private final BookmarkJsonCodec codec = new BookmarkJsonCodec();

    @Test
    void extractsUniquePageReferences() throws BookmarkStoreException
    {
        assertEquals(Set.of("wiki:Space.Page", "wiki:Other.Page"),
            codec.getPageReferences("""
                {"folders":[],"bookmarks":[
                  {"page":"wiki:Space.Page","label":"Page"},
                  {"page":"wiki:Other.Page","label":"Other"},
                  {"page":"wiki:Space.Page","label":"Duplicate"}]}
                """));
    }

    @Test
    void replacesOnlyExactBookmarkPageReferences() throws BookmarkStoreException
    {
        String json = """
            {"folders":[{"name":"wiki:Space.Page"}],"bookmarks":[
              {"page":"wiki:Space.Page","label":"wiki:Space.Page"},
              {"page":"wiki:Space.Page.Sub","label":"Other"}]}
            """;

        String replaced = codec.replacePage(json, "wiki:Space.Page", "wiki:Renamed.Page");

        assertEquals(Set.of("wiki:Renamed.Page", "wiki:Space.Page.Sub"), codec.getPageReferences(replaced));
    }

    @Test
    void rejectsMalformedOrUnexpectedData()
    {
        assertThrows(BookmarkStoreException.class, () -> codec.getPageReferences("{"));
        assertThrows(BookmarkInvalidDataException.class, () -> codec.read((String) null));
        assertThrows(BookmarkInvalidDataException.class, () -> codec.read("  "));
        assertThrows(BookmarkStoreException.class, () -> codec.getPageReferences("{\"folders\":[]}"));
        assertThrows(BookmarkStoreException.class,
            () -> codec.getPageReferences("{\"folders\":[],\"bookmarks\":[{\"page\":null}]}"));
    }

    @Test
    void rejectsOversizedData()
    {
        // Not blank, which is rejected as invalid before its size is checked.
        String json = "{" + " ".repeat(BookmarkJsonCodec.MAX_JSON_BYTES);

        assertThrows(BookmarkTooLargeException.class, () -> codec.getPageReferences(json));
        assertThrows(BookmarkTooLargeException.class, () -> codec.read(json));
    }

    @Test
    void refusesARenamingThatWouldExceedTheMaximumSize() throws Exception
    {
        BookmarkData data = dataOfSize(BookmarkJsonCodec.MAX_JSON_BYTES);
        String json = codec.write(data);

        assertThrows(BookmarkTooLargeException.class,
            () -> codec.replacePage(json, "wiki:Space.Page", "wiki:Space.LongerPage"));
    }

    @Test
    void measuresTheSizeOfTheWrittenJsonInUtf8Bytes() throws Exception
    {
        BookmarkEntry bookmark = new BookmarkEntry();
        bookmark.setPage("wiki:Space.Page");
        // Each accented letter takes two bytes in UTF-8: the limit is on bytes, as in Lucene.
        bookmark.setLabel("Réunion");
        BookmarkData data = new BookmarkData();
        data.setBookmarks(List.of(bookmark));

        assertEquals(codec.write(data).getBytes(StandardCharsets.UTF_8).length, codec.getSize(data));
        assertEquals(codec.write(data).length() + 1, codec.getSize(data));
    }

    @Test
    void acceptsDataOfExactlyTheMaximumSize() throws Exception
    {
        assertEquals(BookmarkJsonCodec.MAX_JSON_BYTES, codec.write(dataOfSize(BookmarkJsonCodec.MAX_JSON_BYTES))
            .getBytes(StandardCharsets.UTF_8).length);
        assertThrows(BookmarkTooLargeException.class,
            () -> codec.write(dataOfSize(BookmarkJsonCodec.MAX_JSON_BYTES + 1)));
    }

    @Test
    void readsTypedDataAndIgnoresUnknownProperties() throws BookmarkStoreException
    {
        BookmarkData data = codec.read("""
            {"folders":[{"id":"f1","name":"Work","parent":"","order":2,"color":"red"}],
             "bookmarks":[{"page":"wiki:Space.Page","label":"Page","folder":"f1","order":1,"panel":true}],
             "future":true}
            """);

        assertEquals(0L, data.getVersion());
        BookmarkFolder folder = data.getFolders().get(0);
        assertEquals("f1", folder.getId());
        assertEquals("Work", folder.getName());
        assertEquals(2, folder.getOrder());
        BookmarkEntry bookmark = data.getBookmarks().get(0);
        assertEquals("wiki:Space.Page", bookmark.getPage());
        assertEquals("f1", bookmark.getFolder());
        assertTrue(bookmark.isPanel());
    }

    @Test
    void writesOnlyFoldersAndBookmarks() throws BookmarkStoreException
    {
        BookmarkEntry bookmark = new BookmarkEntry();
        bookmark.setPage("wiki:Space.Page");
        bookmark.setLabel("Page");
        BookmarkData data = new BookmarkData();
        data.setBookmarks(java.util.List.of(bookmark));
        data.setVersion(42L);

        String json = codec.write(data);

        assertEquals("{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:Space.Page\",\"label\":\"Page\","
            + "\"folder\":\"\",\"order\":0,\"panel\":false}]}", json);
        assertEquals("wiki:Space.Page", codec.read(json).getBookmarks().get(0).getPage());
    }

    @Test
    void writesAndChecksAHeader() throws BookmarkStoreException
    {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("format", "test");
        header.put("formatVersion", 1);
        BookmarkData data = new BookmarkData();

        String json = codec.write(data, header);

        assertEquals("{\"format\":\"test\",\"formatVersion\":1,\"folders\":[],\"bookmarks\":[]}", json);
        assertTrue(codec.read(json, header).getBookmarks().isEmpty());
        assertThrows(BookmarkInvalidDataException.class,
            () -> codec.read(json, Map.of("format", "test", "formatVersion", 2)));
        assertThrows(BookmarkInvalidDataException.class, () -> codec.read(json, Map.of("missing", "test")));
        assertThrows(BookmarkInvalidDataException.class,
            () -> codec.read("{\"format\":\"test\",\"formatVersion\":\"1\",\"folders\":[],\"bookmarks\":[]}",
                header));
    }

    @Test
    void refusesToWriteOversizedData()
    {
        BookmarkEntry bookmark = new BookmarkEntry();
        bookmark.setPage("wiki:Space.Page");
        bookmark.setLabel("l".repeat(BookmarkJsonCodec.MAX_JSON_BYTES));
        BookmarkData data = new BookmarkData();
        data.setBookmarks(List.of(bookmark));

        assertThrows(BookmarkTooLargeException.class, () -> codec.write(data));
    }

    @Test
    void rejectsDataOfTheWrongType()
    {
        assertThrows(BookmarkInvalidDataException.class,
            () -> codec.read("{\"folders\":[],\"bookmarks\":[{\"order\":\"first\"}]}"));
        assertThrows(BookmarkInvalidDataException.class, () -> codec.read("[]"));
        assertThrows(BookmarkInvalidDataException.class,
            () -> codec.getPageReferences("{\"folders\":[],\"bookmarks\":[\"wiki:Space.Page\"]}"));
    }

    @Test
    void rejectsTooManyItems()
    {
        String json = "{\"folders\":[],\"bookmarks\":[" + "{},".repeat(BookmarkJsonCodec.MAX_ITEMS) + "{}]}";

        assertThrows(BookmarkInvalidDataException.class, () -> codec.read(json));
    }

    @Test
    void convertsDataToAndFromAJsonObject() throws Exception
    {
        BookmarkEntry bookmark = new BookmarkEntry();
        bookmark.setPage("Space.Page");
        BookmarkData data = new BookmarkData();
        data.setBookmarks(List.of(bookmark));

        ObjectNode tree = codec.toTree(data);
        assertEquals(codec.write(data), tree.toString());

        // Other properties of the object, such as the user of a wiki export, are ignored.
        tree.put("user", "XWiki.alice");
        assertEquals("Space.Page", codec.read((JsonNode) tree).getBookmarks().get(0).getPage());
    }

    @Test
    void rejectsAnInvalidJsonObject() throws Exception
    {
        ObjectMapper json = new ObjectMapper();
        assertThrows(BookmarkInvalidDataException.class, () -> codec.read((JsonNode) null));
        assertThrows(BookmarkInvalidDataException.class, () -> codec.read(json.readTree("[]")));
        assertThrows(BookmarkInvalidDataException.class, () -> codec.read(json.readTree("{\"folders\":[]}")));
        String oversized = "{\"folders\":[],\"bookmarks\":[{\"label\":\""
            + "l".repeat(BookmarkJsonCodec.MAX_JSON_BYTES) + "\"}]}";
        assertThrows(BookmarkTooLargeException.class, () -> codec.read(json.readTree(oversized)));
    }

    /**
     * @return data with a single bookmark whose JSON has exactly the given size, in bytes
     */
    private BookmarkData dataOfSize(int size)
    {
        BookmarkEntry bookmark = new BookmarkEntry();
        bookmark.setPage("wiki:Space.Page");
        bookmark.setLabel("");
        BookmarkData data = new BookmarkData();
        data.setBookmarks(List.of(bookmark));
        bookmark.setLabel("l".repeat(size - codec.getSize(data)));
        return data;
    }
}

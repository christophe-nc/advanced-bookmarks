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

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertThrows(BookmarkStoreException.class, () -> codec.getPageReferences("{\"folders\":[]}"));
        assertThrows(BookmarkStoreException.class,
            () -> codec.getPageReferences("{\"folders\":[],\"bookmarks\":[{\"page\":null}]}"));
    }

    @Test
    void rejectsOversizedData()
    {
        String json = " ".repeat(BookmarkJsonCodec.MAX_JSON_BYTES + 1);

        assertThrows(BookmarkStoreException.class, () -> codec.getPageReferences(json));
    }
}

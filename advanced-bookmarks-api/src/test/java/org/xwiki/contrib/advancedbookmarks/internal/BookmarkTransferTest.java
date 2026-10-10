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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkFolder;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkManager;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the export file and the import of such a file, by replacement and by merge.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ BookmarkJsonCodec.class, BookmarkNormalizer.class })
class BookmarkTransferTest
{
    private static final DocumentReference USER = ReferenceStubs.page("wiki:XWiki.alice");

    private static final Map<String, Object> HEADER = Map.of("format", "advanced-bookmarks", "formatVersion", 1);

    private static final String HEADER_JSON = "{\"format\":\"advanced-bookmarks\",\"formatVersion\":1,";

    @InjectMockComponents
    private BookmarkTransfer transfer;

    @MockComponent
    private BookmarkManager bookmarkManager;

    @MockComponent
    private BookmarkRights rights;

    @MockComponent
    private BookmarkConfiguration configuration;

    private BookmarkJsonCodec jsonCodec;

    @BeforeComponent
    void registerReferences(MockitoComponentManager componentManager) throws Exception
    {
        ReferenceStubs.register(componentManager);
    }

    @BeforeEach
    void setUp(MockitoComponentManager componentManager) throws Exception
    {
        this.jsonCodec = componentManager.getInstance(BookmarkJsonCodec.class);
        BookmarkNormalizer normalizer = componentManager.getInstance(BookmarkNormalizer.class);
        when(this.rights.getCurrentWiki()).thenReturn(ReferenceStubs.WIKI);
        when(this.configuration.getPanelLimit()).thenReturn(BookmarkConfiguration.DEFAULT_PANEL_LIMIT);
        // Like the real manager, the save normalizes the data in place.
        when(this.bookmarkManager.save(eq(USER), any())).thenAnswer(invocation -> {
            normalizer.normalize(invocation.getArgument(1), ReferenceStubs.WIKI);
            return 8L;
        });
    }

    @Test
    void exportsThePagesWithoutTheirWikiAfterAHeader() throws Exception
    {
        when(this.bookmarkManager.getBookmarks(USER))
            .thenReturn(data(5L, List.of(folder("f", "Folder", "", 0)),
                List.of(bookmark("wiki:Space.Page", "f", 0, true))));

        String json = this.transfer.export(USER);

        assertTrue(json.startsWith(HEADER_JSON + "\"exportedAt\":\""), json);
        assertTrue(json.contains("{\"page\":\"Space.Page\",\"label\":\"Label\",\"folder\":\"f\",\"order\":0,"
            + "\"panel\":true}"), json);
        assertFalse(json.contains("version"), json);
        assertEquals("f", this.jsonCodec.read(json, HEADER).getFolders().get(0).getId());
    }

    @Test
    void replacesTheBookmarksWithTheFile() throws Exception
    {
        when(this.bookmarkManager.getBookmarks(USER))
            .thenReturn(data(5L, List.of(), List.of(bookmark("wiki:Space.Old", "", 0, false))));
        String json = file(data(0L, List.of(), List.of(bookmark("Space.New", "", 0, false),
            bookmark("other:Space.Page", "", 1, false))));

        assertEquals(new BookmarkTransfer.ImportResult(8L, 1, 1),
            this.transfer.importBookmarks(USER, json, true, 5L));

        BookmarkData saved = captureSaved();
        assertEquals(5L, saved.getVersion());
        assertEquals(List.of("wiki:Space.New"), saved.getBookmarks().stream().map(BookmarkEntry::getPage).toList());
    }

    @Test
    void skipsExcludedPagesInBothModes() throws Exception
    {
        when(this.configuration.isExcluded(ReferenceStubs.page("wiki:Archives.Page"))).thenReturn(true);
        String json = file(data(0L, List.of(), List.of(bookmark("Archives.Page", "", 0, false),
            bookmark("Space.New", "", 1, false))));

        for (boolean replace : List.of(true, false)) {
            when(this.bookmarkManager.getBookmarks(USER)).thenReturn(data(5L, List.of(), List.of()));

            assertEquals(new BookmarkTransfer.ImportResult(8L, 1, 1),
                this.transfer.importBookmarks(USER, json, replace, 5L));
        }
        ArgumentCaptor<BookmarkData> saved = ArgumentCaptor.forClass(BookmarkData.class);
        verify(this.bookmarkManager, times(2)).save(eq(USER), saved.capture());
        for (BookmarkData data : saved.getAllValues()) {
            assertEquals(List.of("wiki:Space.New"), data.getBookmarks().stream().map(BookmarkEntry::getPage).toList());
        }
    }

    @Test
    void mergesFoldersOfTheSameNameAtTheSamePlace() throws Exception
    {
        when(this.bookmarkManager.getBookmarks(USER)).thenReturn(data(5L, List.of(folder("x", "Products", "", 7)),
            List.of(bookmark("wiki:Space.Kept", "", 3, false), bookmark("wiki:Space.Duplicate", "x", 0, false))));
        // "p" matches the existing "x" despite the case and the spaces; "s" matches the imported "r".
        String json = file(data(0L,
            List.of(folder("p", " products ", "", 0), folder("q", "Works", "p", 0), folder("r", "Home", "", 1),
                folder("s", "HOME", "", 2)),
            List.of(bookmark("Space.Duplicate", "", 0, false), bookmark("Space.New", "p", 0, false),
                bookmark("Space.Deep", "q", 0, false), bookmark("Space.House", "r", 0, false),
                bookmark("Space.Garage", "s", 0, false), bookmark("Space.Root", "", 5, false))));

        assertEquals(new BookmarkTransfer.ImportResult(8L, 5, 1),
            this.transfer.importBookmarks(USER, json, false, 5L));

        BookmarkData saved = captureSaved();
        assertEquals(5L, saved.getVersion());
        List<BookmarkFolder> folders = saved.getFolders();
        assertEquals(3, folders.size());
        assertEquals(List.of("x", "Products", "", 0), describe(folders.get(0)));
        BookmarkFolder home = folders.get(1);
        assertEquals(List.of(home.getId(), "Home", "", 1), describe(home));
        BookmarkFolder works = folders.get(2);
        assertEquals(List.of(works.getId(), "Works", "x", 0), describe(works));
        assertNotEquals(home.getId(), works.getId());

        assertEquals(List.of(List.of("wiki:Space.Kept", "", 0), List.of("wiki:Space.Duplicate", "x", 0),
            List.of("wiki:Space.New", "x", 1), List.of("wiki:Space.Deep", works.getId(), 0),
            List.of("wiki:Space.House", home.getId(), 0), List.of("wiki:Space.Garage", home.getId(), 1),
            List.of("wiki:Space.Root", "", 1)), saved.getBookmarks().stream().map(BookmarkTransferTest::describe)
            .toList());
    }

    @Test
    void mergingAnExportOfTheSameBookmarksChangesNothing() throws Exception
    {
        when(this.bookmarkManager.getBookmarks(USER)).thenReturn(data(5L, List.of(folder("x", "Products", "", 0)),
            List.of(bookmark("wiki:Space.Page", "x", 0, false))));
        String json = file(data(0L, List.of(folder("x", "Products", "", 0)),
            List.of(bookmark("Space.Page", "x", 0, false))));

        assertEquals(new BookmarkTransfer.ImportResult(8L, 0, 1),
            this.transfer.importBookmarks(USER, json, false, 5L));

        BookmarkData saved = captureSaved();
        assertEquals(List.of(List.of("x", "Products", "", 0)),
            saved.getFolders().stream().map(BookmarkTransferTest::describe).toList());
        assertEquals(List.of(List.of("wiki:Space.Page", "x", 0)),
            saved.getBookmarks().stream().map(BookmarkTransferTest::describe).toList());
    }

    @Test
    void keepsTheExistingPanelSelectionFirst() throws Exception
    {
        List<BookmarkEntry> existing = new ArrayList<>();
        for (int i = 0; i < BookmarkConfiguration.DEFAULT_PANEL_LIMIT; i++) {
            existing.add(bookmark("wiki:Space.Page" + i, "", i, true));
        }
        when(this.bookmarkManager.getBookmarks(USER)).thenReturn(data(5L, List.of(), existing));
        String json = file(data(0L, List.of(), List.of(bookmark("Space.New", "", 0, true))));

        this.transfer.importBookmarks(USER, json, false, 5L);

        List<BookmarkEntry> saved = captureSaved().getBookmarks();
        int limit = BookmarkConfiguration.DEFAULT_PANEL_LIMIT;
        assertTrue(saved.subList(0, limit).stream().allMatch(BookmarkEntry::isPanel));
        assertFalse(saved.get(limit).isPanel());
    }

    @Test
    void refusesAFileOfAnotherFormat() throws Exception
    {
        when(this.bookmarkManager.getBookmarks(USER)).thenReturn(data(0L, List.of(), List.of()));

        assertThrows(BookmarkInvalidDataException.class,
            () -> this.transfer.importBookmarks(USER, "{\"folders\":[],\"bookmarks\":[]}", false, 0L));
        assertThrows(BookmarkInvalidDataException.class, () -> this.transfer.importBookmarks(USER,
            "{\"format\":\"advanced-bookmarks\",\"formatVersion\":2,\"folders\":[],\"bookmarks\":[]}", true, 0L));
        verify(this.bookmarkManager, never()).save(any(), any());
    }

    @Test
    void reportsAConflictWhenTheBookmarksChangedSinceTheyWereRead() throws Exception
    {
        when(this.bookmarkManager.getBookmarks(USER)).thenReturn(data(6L, List.of(), List.of()));
        String json = file(data(0L, List.of(), List.of()));

        assertThrows(BookmarkStoreConflictException.class, () -> this.transfer.importBookmarks(USER, json, true, 5L));
        verify(this.bookmarkManager, never()).save(any(), any());
    }

    @Test
    void checksTheRightsBeforeReadingTheFile() throws Exception
    {
        when(this.bookmarkManager.getBookmarks(USER)).thenThrow(new BookmarkAccessDeniedException());

        assertThrows(BookmarkAccessDeniedException.class, () -> this.transfer.importBookmarks(USER, "{", true, 0L));
        assertThrows(BookmarkAccessDeniedException.class, () -> this.transfer.export(USER));
    }

    private BookmarkData captureSaved() throws Exception
    {
        ArgumentCaptor<BookmarkData> saved = ArgumentCaptor.forClass(BookmarkData.class);
        verify(this.bookmarkManager).save(eq(USER), saved.capture());
        return saved.getValue();
    }

    private String file(BookmarkData data) throws Exception
    {
        return this.jsonCodec.write(data, HEADER);
    }

    private static List<Object> describe(BookmarkFolder folder)
    {
        return List.of(folder.getId(), folder.getName(), folder.getParent(), folder.getOrder());
    }

    private static List<Object> describe(BookmarkEntry bookmark)
    {
        return List.of(bookmark.getPage(), bookmark.getFolder(), bookmark.getOrder());
    }

    private static BookmarkData data(long version, List<BookmarkFolder> folders, List<BookmarkEntry> bookmarks)
    {
        BookmarkData data = new BookmarkData();
        data.setFolders(folders);
        data.setBookmarks(bookmarks);
        data.setVersion(version);
        return data;
    }

    private static BookmarkFolder folder(String id, String name, String parent, int order)
    {
        BookmarkFolder folder = new BookmarkFolder();
        folder.setId(id);
        folder.setName(name);
        folder.setParent(parent);
        folder.setOrder(order);
        return folder;
    }

    private static BookmarkEntry bookmark(String page, String folder, int order, boolean panel)
    {
        BookmarkEntry bookmark = new BookmarkEntry();
        bookmark.setPage(page);
        bookmark.setLabel("Label");
        bookmark.setFolder(folder);
        bookmark.setOrder(order);
        bookmark.setPanel(panel);
        return bookmark;
    }
}

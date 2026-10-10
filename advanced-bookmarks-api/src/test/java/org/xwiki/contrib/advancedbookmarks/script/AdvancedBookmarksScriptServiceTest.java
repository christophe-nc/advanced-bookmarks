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
package org.xwiki.contrib.advancedbookmarks.script;

import java.util.List;
import java.util.Map;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkFolder;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkManager;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkJsonCodec;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkPages;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkReferences;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkRequests;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkRights;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkTransfer;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkViews;
import org.xwiki.contrib.advancedbookmarks.internal.ReferenceStubs;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the script service: request checks, user resolution, result maps and error codes.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ BookmarkJsonCodec.class, BookmarkReferences.class, BookmarkViews.class })
class AdvancedBookmarksScriptServiceTest
{
    private static final DocumentReference ALICE = ReferenceStubs.page("wiki:XWiki.alice");

    private static final DocumentReference BOB = ReferenceStubs.page("wiki:XWiki.bob");

    private static final DocumentReference PAGE = ReferenceStubs.page("wiki:Space.Page");

    private static final String PAGE_KEY = "wiki:Space.Page";

    private static final String STATUS = "status";

    private static final String ERROR = "error";

    private static final String JSON = "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:Space.Page\"}]}";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private AdvancedBookmarksScriptService service;

    @MockComponent
    private BookmarkManager bookmarkManager;

    @MockComponent
    private BookmarkRights rights;

    @MockComponent
    private BookmarkPages pages;

    @MockComponent
    private BookmarkRequests requests;

    @MockComponent
    private BookmarkTransfer transfer;

    @BeforeComponent
    void registerReferences(MockitoComponentManager componentManager) throws Exception
    {
        ReferenceStubs.register(componentManager);
    }

    @BeforeEach
    void setUp()
    {
        when(this.requests.isPost()).thenReturn(true);
        when(this.requests.hasValidToken()).thenReturn(true);
        when(this.rights.getCurrentUser()).thenReturn(ALICE);
        when(this.rights.canView(any())).thenReturn(true);
        when(this.pages.exists(any())).thenReturn(true);
        when(this.pages.getURL(PAGE)).thenReturn("/bin/view/Space/Page");
    }

    @Test
    void returnsTheBookmarksOfTheCurrentUserReadyForJson() throws Exception
    {
        BookmarkData data = data();
        when(this.bookmarkManager.getBookmarks(ALICE)).thenReturn(data);
        when(this.bookmarkManager.getDisplayOrder(data)).thenReturn(data.getBookmarks());

        Map<String, Object> result = this.service.getBookmarks("");

        assertEquals(200, result.get(STATUS));
        assertEquals("wiki:XWiki.alice", result.get("user"));
        assertEquals("9", result.get("version"));
        assertEquals(List.of(Map.of("id", "f", "name", "Folder", "parent", "", "order", 0)), result.get("folders"));
        assertEquals(List.of(Map.of("page", PAGE_KEY, "label", "Label", "folder", "", "order", 0, "panel", true,
            "viewable", true, "exists", true, "url", "/bin/view/Space/Page")), result.get("bookmarks"));
    }

    @Test
    void hidesLiveInformationAboutPagesTheCurrentUserCannotView() throws Exception
    {
        BookmarkData data = data();
        when(this.bookmarkManager.getBookmarks(ALICE)).thenReturn(data);
        when(this.bookmarkManager.getPanel(data)).thenReturn(data.getBookmarks());
        when(this.rights.canView(PAGE)).thenReturn(false);

        Map<String, Object> result = this.service.getPanel();

        assertEquals(List.of(Map.of("page", PAGE_KEY, "label", "Label", "folder", "", "order", 0, "panel", true,
            "viewable", false)), result.get("bookmarks"));
        verifyNoInteractions(this.pages);
    }

    @Test
    void resolvesAnotherUserInTheUserSpace() throws Exception
    {
        when(this.bookmarkManager.getBookmarks(BOB)).thenReturn(new BookmarkData());

        assertEquals("wiki:XWiki.bob", this.service.getBookmarks("bob").get("user"));
    }

    @Test
    void reportsAccessDenialWithoutDetail() throws Exception
    {
        when(this.bookmarkManager.getBookmarks(BOB)).thenThrow(new BookmarkAccessDeniedException());

        assertEquals(Map.of(STATUS, 403, ERROR, "forbidden"), this.service.getBookmarks("XWiki.bob"));
    }

    @Test
    void reportsStorageFailuresWithoutDetail() throws Exception
    {
        when(this.bookmarkManager.getBookmarks(ALICE)).thenThrow(new BookmarkStoreException("secret", null));

        assertEquals(Map.of(STATUS, 500, ERROR, "storageError"), this.service.getBookmarks(null));
        assertEquals("Advanced Bookmarks operation failed.", this.logCapture.getMessage(0));
    }

    @Test
    void rejectsInvalidReferences()
    {
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.isBookmarked(" "));
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.isBookmarked("p".repeat(513)));
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.getBookmarks("u".repeat(513)));
        verifyNoInteractions(this.bookmarkManager);
    }

    @Test
    void tellsWhetherAPageIsBookmarked() throws Exception
    {
        when(this.bookmarkManager.isBookmarked(ALICE, PAGE)).thenReturn(true);

        assertEquals(Map.of(STATUS, 200, "bookmarked", true), this.service.isBookmarked(PAGE_KEY));
    }

    @Test
    void togglesAPageOnAValidPostRequest() throws Exception
    {
        when(this.bookmarkManager.toggle(ALICE, PAGE)).thenReturn(false);

        assertEquals(Map.of(STATUS, 200, "bookmarked", false), this.service.toggle(PAGE_KEY));
    }

    @Test
    void refusesWritesWithoutPost()
    {
        when(this.requests.isPost()).thenReturn(false);

        assertEquals(Map.of(STATUS, 405, ERROR, "methodNotAllowed"), this.service.toggle(PAGE_KEY));
        assertEquals(Map.of(STATUS, 405, ERROR, "methodNotAllowed"), this.service.save("", JSON, "0"));
        assertEquals(Map.of(STATUS, 405, ERROR, "methodNotAllowed"),
            this.service.importBookmarks("", JSON, "merge", "0"));
        verifyNoInteractions(this.bookmarkManager, this.transfer);
    }

    @Test
    void refusesWritesWithAnInvalidToken()
    {
        when(this.requests.hasValidToken()).thenReturn(false);

        assertEquals(Map.of(STATUS, 403, ERROR, "invalidToken"), this.service.toggle(PAGE_KEY));
        assertEquals(Map.of(STATUS, 403, ERROR, "invalidToken"), this.service.save("", JSON, "0"));
        assertEquals(Map.of(STATUS, 403, ERROR, "invalidToken"), this.service.importBookmarks("", JSON, "merge", "0"));
        verifyNoInteractions(this.bookmarkManager, this.transfer);
    }

    @Test
    void reportsAToggleConflict() throws Exception
    {
        when(this.bookmarkManager.toggle(ALICE, PAGE)).thenThrow(new BookmarkStoreConflictException(null));

        assertEquals(Map.of(STATUS, 409, ERROR, "conflict"), this.service.toggle(PAGE_KEY));
    }

    @Test
    void savesTheBookmarksWithTheVersionTheyWereReadAt() throws Exception
    {
        when(this.bookmarkManager.save(any(), any())).thenReturn(4L);

        assertEquals(Map.of(STATUS, 200, "version", "4"), this.service.save("bob", JSON, "3"));

        ArgumentCaptor<BookmarkData> data = ArgumentCaptor.forClass(BookmarkData.class);
        verify(this.bookmarkManager).save(org.mockito.ArgumentMatchers.eq(BOB), data.capture());
        assertEquals(3L, data.getValue().getVersion());
        assertEquals(PAGE_KEY, data.getValue().getBookmarks().get(0).getPage());
    }

    @Test
    void exchangesSolrVersionsWithoutLosingPrecision() throws Exception
    {
        // Solr versions exceed 2^53, the integer precision of JavaScript numbers.
        long solrVersion = 1_812_345_678_901_234_567L;
        BookmarkData stored = new BookmarkData();
        stored.setVersion(solrVersion);
        when(this.bookmarkManager.getBookmarks(ALICE)).thenReturn(stored);
        when(this.bookmarkManager.save(any(), any())).thenReturn(solrVersion + 1);

        assertEquals("1812345678901234567", this.service.getBookmarks("").get("version"));
        assertEquals("1812345678901234568", this.service.save("", JSON, "1812345678901234567").get("version"));

        ArgumentCaptor<BookmarkData> data = ArgumentCaptor.forClass(BookmarkData.class);
        verify(this.bookmarkManager).save(org.mockito.ArgumentMatchers.eq(ALICE), data.capture());
        assertEquals(solrVersion, data.getValue().getVersion());
    }

    @Test
    void rejectsInvalidSavedData() throws Exception
    {
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.save("", "{", "0"));
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.save("", JSON, "-1"));
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.save("", JSON, "1.5"));
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.save("", JSON, null));

        when(this.bookmarkManager.save(any(), any())).thenThrow(new BookmarkInvalidDataException(null));
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.save("", JSON, "0"));
    }

    @Test
    void reportsASaveConflict() throws Exception
    {
        when(this.bookmarkManager.save(any(), any())).thenThrow(new BookmarkStoreConflictException(null));

        Map<String, Object> result = this.service.save("", JSON, "2");

        assertEquals(Map.of(STATUS, 409, ERROR, "conflict"), result);
        assertFalse(result.containsKey("version"));
    }

    @Test
    void exportsTheBookmarksOfAUser() throws Exception
    {
        when(this.transfer.export(BOB)).thenReturn(JSON);

        assertEquals(Map.of(STATUS, 200, "content", JSON), this.service.exportBookmarks("bob"));
    }

    @Test
    void reportsAnExportDenial() throws Exception
    {
        when(this.transfer.export(ALICE)).thenThrow(new BookmarkAccessDeniedException());

        assertEquals(Map.of(STATUS, 403, ERROR, "forbidden"), this.service.exportBookmarks(""));
    }

    @Test
    void importsAFileInTheChosenMode() throws Exception
    {
        when(this.transfer.importBookmarks(BOB, JSON, true, 3L))
            .thenReturn(new BookmarkTransfer.ImportResult(4L, 2, 1));
        when(this.transfer.importBookmarks(ALICE, JSON, false, 0L))
            .thenReturn(new BookmarkTransfer.ImportResult(1L, 1, 0));

        assertEquals(Map.of(STATUS, 200, "version", "4", "imported", 2, "skipped", 1),
            this.service.importBookmarks("bob", JSON, "replace", "3"));
        assertEquals(Map.of(STATUS, 200, "version", "1", "imported", 1, "skipped", 0),
            this.service.importBookmarks("", JSON, "merge", "0"));
    }

    @Test
    void rejectsAnInvalidImportRequest() throws Exception
    {
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.importBookmarks("", JSON, "append", "0"));
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.importBookmarks("", JSON, null, "0"));
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.importBookmarks("", JSON, "merge", "x"));
        verifyNoInteractions(this.transfer);

        when(this.transfer.importBookmarks(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
            org.mockito.ArgumentMatchers.anyLong())).thenThrow(new BookmarkStoreConflictException(null));
        assertEquals(Map.of(STATUS, 409, ERROR, "conflict"), this.service.importBookmarks("", JSON, "merge", "0"));
    }

    private static BookmarkData data()
    {
        BookmarkFolder folder = new BookmarkFolder();
        folder.setId("f");
        folder.setName("Folder");
        BookmarkEntry bookmark = new BookmarkEntry();
        bookmark.setPage(PAGE_KEY);
        bookmark.setLabel("Label");
        bookmark.setPanel(true);
        BookmarkData data = new BookmarkData();
        data.setFolders(List.of(folder));
        data.setBookmarks(List.of(bookmark));
        data.setVersion(9L);
        return data;
    }
}

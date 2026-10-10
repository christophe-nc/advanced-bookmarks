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
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStore;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkRequests;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkResults;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkRights;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkWikiImportStatus;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkWikiImports;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkWikiTransfer;
import org.xwiki.job.event.status.JobProgress;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the administration tools exposed to scripts.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(BookmarkResults.class)
class AdvancedBookmarksAdminScriptServiceTest
{
    private static final String STATUS = "status";

    private static final String ERROR = "error";

    private static final String STATE = "state";

    private static final byte[] CONTENT = { 1 };

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private AdvancedBookmarksAdminScriptService service;

    @MockComponent
    private BookmarkRights rights;

    @MockComponent
    private BookmarkRequests requests;

    @MockComponent
    private BookmarkStore store;

    @MockComponent
    private BookmarkWikiTransfer wikiTransfer;

    @MockComponent
    private BookmarkWikiImports imports;

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.rights.isAdmin()).thenReturn(true);
        when(this.requests.isPost()).thenReturn(true);
        when(this.requests.hasValidToken()).thenReturn(true);
        when(this.requests.readFile(AdvancedBookmarksAdminScriptService.FILE_PART,
            BookmarkWikiTransfer.MAX_FILE_BYTES)).thenReturn(CONTENT);
    }

    @Test
    void refusesEverythingToUsersWhoDoNotAdministerTheWiki()
    {
        when(this.rights.isAdmin()).thenReturn(false);

        for (Map<String, Object> result : List.of(this.service.getStoreStatus(), this.service.exportWiki(),
            this.service.startWikiImport("merge"), this.service.getWikiImportStatus())) {
            assertEquals(Map.of(STATUS, 403, ERROR, "forbidden"), result);
        }
        verifyNoInteractions(this.store, this.wikiTransfer, this.imports);
    }

    @Test
    void reportsWhetherTheStorageIsAvailable()
    {
        when(this.store.isAvailable()).thenReturn(true, false);

        assertEquals(Map.of(STATUS, 200, "available", true), this.service.getStoreStatus());
        assertEquals(Map.of(STATUS, 200, "available", false), this.service.getStoreStatus());
    }

    @Test
    void exportsTheBookmarksOfTheWiki() throws Exception
    {
        when(this.wikiTransfer.exportWiki()).thenReturn(new BookmarkWikiTransfer.WikiExport("{}", 3, 1));

        assertEquals(Map.of(STATUS, 200, "content", "{}", "exported", 3, "skipped", 1), this.service.exportWiki());
    }

    @Test
    void reportsExportFailuresWithoutDetail() throws Exception
    {
        when(this.wikiTransfer.exportWiki()).thenThrow(new BookmarkInvalidDataException(null),
            new BookmarkStoreException("down", null));

        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.exportWiki());
        assertEquals(Map.of(STATUS, 500, ERROR, "storageError"), this.service.exportWiki());
        assertEquals("Advanced Bookmarks operation failed.", this.logCapture.getMessage(0));
    }

    @Test
    void startsTheImportOfTheUploadedFile() throws Exception
    {
        when(this.imports.start(CONTENT, true)).thenReturn(12);

        assertEquals(Map.of(STATUS, 200, "users", 12), this.service.startWikiImport("replace"));
    }

    @Test
    void checksTheImportRequestBeforeReadingTheFile() throws Exception
    {
        assertEquals(Map.of(STATUS, 400, ERROR, "invalid"), this.service.startWikiImport("other"));

        when(this.requests.hasValidToken()).thenReturn(false);
        assertEquals(Map.of(STATUS, 403, ERROR, "invalidToken"), this.service.startWikiImport("merge"));

        when(this.requests.isPost()).thenReturn(false);
        assertEquals(Map.of(STATUS, 405, ERROR, "methodNotAllowed"), this.service.startWikiImport("merge"));

        verify(this.requests, never()).readFile(any(), anyInt());
        verify(this.imports, never()).start(any(), anyBoolean());
    }

    @Test
    void refusesASecondImportWhileOneIsRunning() throws Exception
    {
        when(this.imports.start(CONTENT, false)).thenThrow(new BookmarkStoreConflictException(null));

        assertEquals(Map.of(STATUS, 409, ERROR, "conflict"), this.service.startWikiImport("merge"));
    }

    @Test
    void reportsTheStatusOfTheImport()
    {
        assertEquals(Map.of(STATUS, 200, STATE, "none"), this.service.getWikiImportStatus());

        BookmarkWikiImportStatus status = mock(BookmarkWikiImportStatus.class);
        JobProgress progress = mock(JobProgress.class);
        when(progress.getOffset()).thenReturn(0.425);
        when(status.getProgress()).thenReturn(progress);
        when(status.getCounts()).thenReturn(new BookmarkWikiImportStatus.Counts(1, 2, 3, 4, 5));
        when(status.getState()).thenReturn(JobStatus.State.RUNNING);
        when(this.imports.getStatus()).thenReturn(status);
        Map<String, Object> result = this.service.getWikiImportStatus();
        assertEquals("running", result.get(STATE));
        assertEquals(42, result.get("progress"));
        assertEquals(List.of(1, 2, 3, 4, 5), List.of(result.get("importedUsers"), result.get("skippedUsers"),
            result.get("failedUsers"), result.get("importedBookmarks"), result.get("skippedBookmarks")));
        assertEquals(false, result.get("failed"));

        when(status.getState()).thenReturn(JobStatus.State.FINISHED);
        when(status.getError()).thenReturn(new IllegalStateException());
        result = this.service.getWikiImportStatus();
        assertEquals("finished", result.get(STATE));
        assertEquals(true, result.get("failed"));
    }
}

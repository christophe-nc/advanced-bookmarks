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

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.context.concurrent.ContextStoreManager;
import org.xwiki.contrib.advancedbookmarks.BookmarkException;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.job.Job;
import org.xwiki.job.JobException;
import org.xwiki.job.JobExecutor;
import org.xwiki.job.JobStatusStore;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the start of wiki imports and the reading of their status.
 *
 * @version $Id$
 */
@ComponentTest
class BookmarkWikiImportsTest
{
    private static final List<String> JOB_ID = List.of("advancedbookmarks", "import", ReferenceStubs.WIKI);

    private static final byte[] CONTENT = { 1, 2 };

    @InjectMockComponents
    private BookmarkWikiImports imports;

    @MockComponent
    private BookmarkWikiTransfer wikiTransfer;

    @MockComponent
    private BookmarkRights rights;

    @MockComponent
    private JobExecutor jobExecutor;

    @MockComponent
    private JobStatusStore jobStatusStore;

    @MockComponent
    private ContextStoreManager contextStore;

    private final Map<String, Serializable> context = Map.of("wiki", ReferenceStubs.WIKI);

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.rights.getCurrentWiki()).thenReturn(ReferenceStubs.WIKI);
        when(this.wikiTransfer.validate(CONTENT)).thenReturn(2);
        when(this.contextStore.save(List.of("wiki", "user", "locale"))).thenReturn(this.context);
    }

    @Test
    void startsTheImportOfAValidatedFileWithTheCurrentContext() throws Exception
    {
        assertEquals(2, this.imports.start(CONTENT, true));

        ArgumentCaptor<BookmarkWikiImportRequest> request = ArgumentCaptor.forClass(BookmarkWikiImportRequest.class);
        verify(this.jobExecutor).execute(eq(BookmarkWikiImportJob.JOB_TYPE), request.capture());
        assertEquals(JOB_ID, request.getValue().getId());
        assertArrayEquals(CONTENT, request.getValue().getContent());
        assertTrue(request.getValue().isReplace());
        assertEquals(2, request.getValue().getUserCount());
        assertFalse(request.getValue().isStatusSerialized());
        assertFalse(request.getValue().isStatusLogIsolated());
        assertSame(this.context, request.getValue().getContext());
    }

    @Test
    void refusesAnInvalidFileWithoutStartingAnything() throws Exception
    {
        when(this.wikiTransfer.validate(CONTENT)).thenThrow(new BookmarkInvalidDataException(null));

        assertThrows(BookmarkInvalidDataException.class, () -> this.imports.start(CONTENT, false));
        verify(this.jobExecutor, never()).execute(any(), any());
    }

    @Test
    void startsOnlyOneImportAtATime() throws Exception
    {
        BookmarkWikiImportStatus status = mock(BookmarkWikiImportStatus.class);
        Job job = mock(Job.class);
        when(job.getStatus()).thenReturn(status);
        when(this.jobExecutor.getJob(JOB_ID)).thenReturn(job);

        when(status.getState()).thenReturn(JobStatus.State.RUNNING);
        assertThrows(BookmarkStoreConflictException.class, () -> this.imports.start(CONTENT, false));
        verify(this.jobExecutor, never()).execute(any(), any());

        when(status.getState()).thenReturn(JobStatus.State.FINISHED);
        assertEquals(2, this.imports.start(CONTENT, false));
        verify(this.jobExecutor).execute(eq(BookmarkWikiImportJob.JOB_TYPE), any());
    }

    @Test
    void reportsAnImportThatCannotBeStarted() throws Exception
    {
        when(this.jobExecutor.execute(any(), any())).thenThrow(new JobException("no job"));
        assertInstanceOf(JobException.class,
            assertThrows(BookmarkException.class, () -> this.imports.start(CONTENT, false)).getCause());

        when(this.contextStore.save(any())).thenThrow(new ComponentLookupException("no store"));
        assertInstanceOf(ComponentLookupException.class,
            assertThrows(BookmarkException.class, () -> this.imports.start(CONTENT, false)).getCause());
    }

    @Test
    void readsTheStatusOfTheRunningOrLastImport()
    {
        assertNull(this.imports.getStatus());

        BookmarkWikiImportStatus finished = mock(BookmarkWikiImportStatus.class);
        when(this.jobStatusStore.getJobStatus(JOB_ID)).thenReturn(finished);
        assertSame(finished, this.imports.getStatus());

        BookmarkWikiImportStatus running = mock(BookmarkWikiImportStatus.class);
        Job job = mock(Job.class);
        when(job.getStatus()).thenReturn(running);
        when(this.jobExecutor.getJob(JOB_ID)).thenReturn(job);
        assertSame(running, this.imports.getStatus());

        // Another kind of job with the same identifier is ignored.
        when(job.getStatus()).thenReturn(mock(JobStatus.class));
        assertNull(this.imports.getStatus());
        assertFalse(BookmarkWikiImports.isRunning(null));
    }
}

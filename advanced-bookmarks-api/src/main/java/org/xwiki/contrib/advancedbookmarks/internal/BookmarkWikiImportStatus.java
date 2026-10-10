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

import org.xwiki.job.DefaultJobStatus;
import org.xwiki.logging.LoggerManager;
import org.xwiki.observation.ObservationManager;

/**
 * The status of a {@link BookmarkWikiImportJob}: progress, log and the counts of the import so far.
 *
 * @version $Id$
 * @since 1.0
 */
public class BookmarkWikiImportStatus extends DefaultJobStatus<BookmarkWikiImportRequest>
{
    private static final long serialVersionUID = 1L;

    // Replaced as a whole by the job thread and read by request threads: each reader sees consistent counts.
    private volatile Counts counts = new Counts(0, 0, 0, 0, 0);

    /**
     * The counts of an import.
     *
     * @param importedUsers the number of users whose bookmarks were imported
     * @param skippedUsers the number of users of the file who are not users of the wiki
     * @param failedUsers the number of users whose bookmarks could not be imported, the reason being logged
     * @param importedBookmarks the number of bookmarks added
     * @param skippedBookmarks the number of bookmarks of the imported users that were not added
     */
    public record Counts(int importedUsers, int skippedUsers, int failedUsers, int importedBookmarks,
        int skippedBookmarks) implements Serializable
    {
    }

    /**
     * @param jobType the type of the job
     * @param request the request of the job
     * @param observationManager the observation manager, to follow the progress of the job
     * @param loggerManager the logger manager, to record the log of the job
     */
    public BookmarkWikiImportStatus(String jobType, BookmarkWikiImportRequest request,
        ObservationManager observationManager, LoggerManager loggerManager)
    {
        super(jobType, request, null, observationManager, loggerManager);
    }

    /**
     * @return the counts of the import so far
     */
    public Counts getCounts()
    {
        return this.counts;
    }

    /**
     * @param result the import of the bookmarks of one user
     */
    public void addImportedUser(BookmarkTransfer.ImportResult result)
    {
        Counts current = this.counts;
        this.counts = new Counts(current.importedUsers() + 1, current.skippedUsers(), current.failedUsers(),
            current.importedBookmarks() + result.imported(), current.skippedBookmarks() + result.skipped());
    }

    /**
     * Count a user of the file who is not a user of the wiki.
     */
    public void addSkippedUser()
    {
        Counts current = this.counts;
        this.counts = new Counts(current.importedUsers(), current.skippedUsers() + 1, current.failedUsers(),
            current.importedBookmarks(), current.skippedBookmarks());
    }

    /**
     * Count a user whose bookmarks could not be imported.
     */
    public void addFailedUser()
    {
        Counts current = this.counts;
        this.counts = new Counts(current.importedUsers(), current.skippedUsers(), current.failedUsers() + 1,
            current.importedBookmarks(), current.skippedBookmarks());
    }
}

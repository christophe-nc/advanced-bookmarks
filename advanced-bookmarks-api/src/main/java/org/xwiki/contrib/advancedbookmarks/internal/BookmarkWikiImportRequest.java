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

import org.xwiki.job.AbstractRequest;
import org.xwiki.job.Request;

/**
 * The request of a {@link BookmarkWikiImportJob}: a validated wiki export file and the import mode.
 *
 * @version $Id$
 * @since 1.0
 */
public class BookmarkWikiImportRequest extends AbstractRequest
{
    private static final long serialVersionUID = 1L;

    private static final String CONTENT = "content";

    private static final String REPLACE = "replace";

    private static final String USER_COUNT = "userCount";

    /**
     * Create an empty request.
     */
    public BookmarkWikiImportRequest()
    {
    }

    /**
     * Copy a request, as required by the job framework.
     *
     * @param request the request to copy
     */
    public BookmarkWikiImportRequest(Request request)
    {
        super(request);
    }

    /**
     * @return the content of the wiki export file, or {@code null} once the import is over
     */
    public byte[] getContent()
    {
        return getProperty(CONTENT);
    }

    /**
     * @param content the content of the wiki export file, already validated
     */
    public void setContent(byte[] content)
    {
        setProperty(CONTENT, content);
    }

    /**
     * Release the content of the file, which can be large, since the status of a finished job is kept in memory with
     * its request.
     */
    public void clearContent()
    {
        removeProperty(CONTENT);
    }

    /**
     * @return {@code true} to replace the bookmarks of each user, {@code false} to merge the file with them
     */
    public boolean isReplace()
    {
        return getProperty(REPLACE, false);
    }

    /**
     * @param replace {@code true} to replace the bookmarks of each user, {@code false} to merge the file with them
     */
    public void setReplace(boolean replace)
    {
        setProperty(REPLACE, replace);
    }

    /**
     * @return the number of users in the file, which gives the number of progress steps
     */
    public int getUserCount()
    {
        return getProperty(USER_COUNT, 0);
    }

    /**
     * @param userCount the number of users in the file
     */
    public void setUserCount(int userCount)
    {
        setProperty(USER_COUNT, userCount);
    }
}

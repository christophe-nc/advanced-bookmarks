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
package org.xwiki.contrib.advancedbookmarks;

import java.util.ArrayList;
import java.util.List;

/**
 * All the folders and bookmarks of one user in one wiki, with the storage version they were read at.
 *
 * @version $Id$
 * @since 1.0
 */
public class BookmarkData
{
    private List<BookmarkFolder> folders = new ArrayList<>();

    private List<BookmarkEntry> bookmarks = new ArrayList<>();

    private long version;

    /**
     * @return the folders, never {@code null}
     */
    public List<BookmarkFolder> getFolders()
    {
        return this.folders;
    }

    /**
     * @param folders the folders; {@code null} is replaced by an empty list
     */
    public void setFolders(List<BookmarkFolder> folders)
    {
        this.folders = folders == null ? new ArrayList<>() : new ArrayList<>(folders);
    }

    /**
     * @return the bookmarks, never {@code null}
     */
    public List<BookmarkEntry> getBookmarks()
    {
        return this.bookmarks;
    }

    /**
     * @param bookmarks the bookmarks; {@code null} is replaced by an empty list
     */
    public void setBookmarks(List<BookmarkEntry> bookmarks)
    {
        this.bookmarks = bookmarks == null ? new ArrayList<>() : new ArrayList<>(bookmarks);
    }

    /**
     * @return the storage version these data were read at, {@code 0} if nothing is stored yet
     */
    public long getVersion()
    {
        return this.version;
    }

    /**
     * @param version the storage version these data were read at
     */
    public void setVersion(long version)
    {
        this.version = version;
    }
}

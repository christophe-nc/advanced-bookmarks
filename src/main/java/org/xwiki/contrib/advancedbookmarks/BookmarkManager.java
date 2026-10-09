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

import java.util.List;

import org.xwiki.component.annotation.Role;
import org.xwiki.model.reference.DocumentReference;

/**
 * Business logic of bookmarks, in the current wiki. Every operation on the bookmarks of a user checks that the
 * current user is allowed to manage them: users manage their own bookmarks, wiki administrators those of anyone.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface BookmarkManager
{
    /** Maximum number of bookmarks displayed in the panel. */
    int MAX_PANEL_SIZE = 10;

    /**
     * @param user the owner of the bookmarks
     * @return {@code true} if the current user may read and modify the bookmarks of {@code user}
     */
    boolean canManage(DocumentReference user);

    /**
     * @param user the owner of the bookmarks
     * @return the normalized bookmarks of the user, empty with version {@code 0} if none are stored
     * @throws BookmarkAccessDeniedException if the current user may not manage these bookmarks
     * @throws BookmarkStoreException if the bookmarks cannot be read
     */
    BookmarkData getBookmarks(DocumentReference user) throws BookmarkException;

    /**
     * @param user the owner of the bookmarks
     * @param page a page
     * @return {@code true} if the page is among the bookmarks of the user
     * @throws BookmarkAccessDeniedException if the current user may not manage these bookmarks
     * @throws BookmarkStoreException if the bookmarks cannot be read
     */
    boolean isBookmarked(DocumentReference user, DocumentReference page) throws BookmarkException;

    /**
     * Add the page to the uncategorized bookmarks of the user, or remove it if it is already bookmarked. Concurrent
     * modifications are retried a few times, since this operation does not depend on what the user saw.
     *
     * @param user the owner of the bookmarks
     * @param page a page
     * @return {@code true} if the page is bookmarked after the operation
     * @throws BookmarkAccessDeniedException if the current user may not manage these bookmarks
     * @throws BookmarkStoreConflictException if the bookmarks keep being modified concurrently
     * @throws BookmarkStoreException if the bookmarks cannot be read or written
     */
    boolean toggle(DocumentReference user, DocumentReference page) throws BookmarkException;

    /**
     * Normalize, then replace all the bookmarks of the user, provided they have not been modified since they were
     * read at {@link BookmarkData#getVersion()}.
     *
     * @param user the owner of the bookmarks
     * @param data the new folders and bookmarks, with the version they were read at
     * @return the new version
     * @throws BookmarkAccessDeniedException if the current user may not manage these bookmarks
     * @throws BookmarkInvalidDataException if the data exceeds the supported limits
     * @throws BookmarkStoreConflictException if the bookmarks were modified since they were read
     * @throws BookmarkStoreException if the bookmarks cannot be written
     */
    long save(DocumentReference user, BookmarkData data) throws BookmarkException;

    /**
     * @param data normalized bookmarks
     * @return the bookmarks in the order of the management page: uncategorized ones first, then each folder in
     *         depth-first order, a folder's subfolders coming before its own bookmarks
     */
    List<BookmarkEntry> getDisplayOrder(BookmarkData data);

    /**
     * @param data normalized bookmarks
     * @return the bookmarks selected for the panel in display order or, if none is selected, the first
     *         {@value #MAX_PANEL_SIZE} bookmarks in display order
     */
    List<BookmarkEntry> getPanel(BookmarkData data);
}

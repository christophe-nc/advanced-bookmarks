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
import java.util.Optional;

import org.xwiki.component.annotation.Role;

/**
 * Provides persistence operations for one user's bookmarks in one wiki.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface BookmarkStore
{
    /**
     * Read a user's bookmarks.
     *
     * @param wiki the wiki identifier
     * @param user the serialized user reference
     * @return the saved data, or an empty result if no document exists
     * @throws BookmarkStoreException if the storage cannot be read
     */
    Optional<BookmarkRecord> get(String wiki, String user) throws BookmarkStoreException;

    /**
     * Save bookmark JSON only if its version has not changed since it was read.
     *
     * @param wiki the wiki identifier
     * @param user the serialized user reference
     * @param json the serialized folders and bookmarks
     * @param expectedVersion the version previously read, or {@code 0} for a new document
     * @return the new Solr document version
     * @throws BookmarkStoreConflictException if the document was concurrently changed
     * @throws BookmarkStoreException if the storage cannot be written or the data is invalid
     */
    long save(String wiki, String user, String json, long expectedVersion)
        throws BookmarkStoreConflictException, BookmarkStoreException;

    /**
     * Delete a user's bookmark document if its version matches.
     *
     * @param wiki the wiki identifier
     * @param user the serialized user reference
     * @param expectedVersion the version previously read, or {@code 0} if no document existed
     * @throws BookmarkStoreConflictException if the document was concurrently changed
     * @throws BookmarkStoreException if the storage cannot be updated
     */
    void delete(String wiki, String user, long expectedVersion)
        throws BookmarkStoreConflictException, BookmarkStoreException;

    /**
     * Replace a page reference in all matching users' bookmarks.
     *
     * @param wiki the wiki identifier
     * @param oldPage the previous serialized page reference
     * @param newPage the replacement serialized page reference
     * @return the number of user documents updated
     * @throws BookmarkStoreConflictException if a matching document keeps changing during the update
     * @throws BookmarkStoreException if the storage cannot complete the update
     */
    int replacePage(String wiki, String oldPage, String newPage)
        throws BookmarkStoreConflictException, BookmarkStoreException;

    /**
     * Find users who have bookmarked a page in the specified wiki.
     *
     * @param wiki the wiki identifier
     * @param page the serialized page reference
     * @return matching serialized user references, in case-insensitive alphabetical order
     * @throws BookmarkStoreException if the storage cannot be queried
     */
    List<String> findUsersByPage(String wiki, String page) throws BookmarkStoreException;

    /**
     * List users with stored bookmarks in the specified wiki.
     *
     * @param wiki the wiki identifier
     * @return serialized user references, in case-insensitive alphabetical order
     * @throws BookmarkStoreException if the storage cannot be queried
     */
    List<String> listUsers(String wiki) throws BookmarkStoreException;

    /**
     * Delete the bookmarks of a user in every wiki of the farm, typically after the user has been deleted.
     *
     * @param user the serialized user reference, including its wiki
     * @throws BookmarkStoreException if the storage cannot be updated
     */
    void deleteUser(String user) throws BookmarkStoreException;

    /**
     * Delete all the bookmarks stored for a wiki, typically after the wiki has been deleted.
     *
     * @param wiki the wiki identifier
     * @throws BookmarkStoreException if the storage cannot be updated
     */
    void deleteWiki(String wiki) throws BookmarkStoreException;
}

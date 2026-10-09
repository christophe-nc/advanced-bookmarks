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

/**
 * Indicates that an optimistic bookmark update did not match the current Solr version.
 *
 * @since 1.0
 */
public class BookmarkStoreConflictException extends BookmarkStoreException
{
    private static final long serialVersionUID = 1L;

    /**
     * Create a version-conflict exception.
     *
     * @param cause the Solr conflict
     */
    public BookmarkStoreConflictException(Throwable cause)
    {
        super("The bookmark document was modified concurrently.", cause);
    }
}

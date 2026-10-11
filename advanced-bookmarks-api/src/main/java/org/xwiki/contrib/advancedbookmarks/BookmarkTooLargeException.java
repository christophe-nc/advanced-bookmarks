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
 * Indicates that the bookmarks of a user would exceed their maximum size. Unlike other invalid data, users can reach
 * this limit through normal use, so it is reported to them with its own error code.
 *
 * @version $Id$
 * @since 1.0
 */
public class BookmarkTooLargeException extends BookmarkInvalidDataException
{
    private static final long serialVersionUID = 1L;

    /**
     * Create an exception for bookmarks exceeding their maximum size.
     */
    public BookmarkTooLargeException()
    {
        super(null);
    }
}

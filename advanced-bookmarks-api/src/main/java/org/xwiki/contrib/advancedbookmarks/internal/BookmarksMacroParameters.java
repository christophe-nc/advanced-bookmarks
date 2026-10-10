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

import org.xwiki.properties.annotation.PropertyDescription;

/**
 * The parameters of the {@code bookmarks} macro.
 *
 * @version $Id$
 * @since 1.0
 */
public class BookmarksMacroParameters
{
    private String user;

    /**
     * @return the user whose bookmarks are displayed, or {@code null} for the current user
     */
    public String getUser()
    {
        return this.user;
    }

    /**
     * @param user the user whose bookmarks are displayed, such as {@code XWiki.jdoe}; empty for the current user
     */
    @PropertyDescription("The user whose bookmarks are displayed, such as XWiki.jdoe. Only this user and the "
        + "administrators of the wiki can view them. Leave empty for the current user.")
    public void setUser(String user)
    {
        this.user = user;
    }
}

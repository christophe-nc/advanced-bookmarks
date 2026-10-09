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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the placeholder rendering of the bookmarks macro.
 *
 * @version $Id$
 */
class BookmarksMacroTest
{
    private final BookmarksMacro macro = new BookmarksMacro();

    @Test
    void usesTheDefaults()
    {
        assertEquals("bookmarks", this.macro.getMacroName());
        assertEquals(BookmarkManager.MAX_PANEL_SIZE, this.macro.getDefaultPanelSize());
        assertEquals("AdvancedBookmarks.WebHome", this.macro.getDefaultHomePage());
    }

    @Test
    void rendersTheDefaultHomePageAndLink()
    {
        String rendered = this.macro.render();

        assertTrue(rendered.contains("Advanced Bookmarks"));
        assertTrue(rendered.contains("AdvancedBookmarks.WebHome"));
        assertTrue(rendered.contains("View all my bookmarks"));
        assertTrue(rendered.contains("data-panel-size=\"10\""));
    }

    @Test
    void supportsThePanelConfiguration()
    {
        String rendered = this.macro.render(8, true, true);

        assertTrue(rendered.contains("data-panel-size=\"8\""));
        assertTrue(rendered.contains("data-read-only=\"true\""));
        assertTrue(rendered.contains("data-compact=\"true\""));
    }

    @Test
    void boundsThePanelSize()
    {
        assertTrue(this.macro.render(100, false, false).contains("data-panel-size=\"10\""));
        assertTrue(this.macro.render(0, false, false).contains("data-panel-size=\"10\""));
    }
}

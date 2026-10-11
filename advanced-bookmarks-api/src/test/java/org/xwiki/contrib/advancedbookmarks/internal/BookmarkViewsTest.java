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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.xwiki.contrib.advancedbookmarks.internal.BookmarkNormalizerTest.bookmark;

/**
 * Tests the conversion of bookmarks for the browser, and what it reveals about the pages.
 *
 * @version $Id$
 */
@ComponentTest
class BookmarkViewsTest
{
    private static final DocumentReference PAGE = ReferenceStubs.page("wiki:Space.Page");

    private static final String PAGE_KEY = "wiki:Space.Page";

    private static final String URL = "/bin/view/Space/Page";

    @InjectMockComponents
    private BookmarkViews views;

    @MockComponent
    private BookmarkRights rights;

    @MockComponent
    private BookmarkPages pages;

    @BeforeComponent
    void registerReferences(MockitoComponentManager componentManager) throws Exception
    {
        ReferenceStubs.register(componentManager);
    }

    @BeforeEach
    void setUp()
    {
        when(this.rights.canView(PAGE)).thenReturn(true);
        when(this.pages.exists(PAGE)).thenReturn(true);
        when(this.pages.getURL(PAGE)).thenReturn(URL);
    }

    @Test
    void givesTheTextToDisplayForABookmarkWithoutLabel()
    {
        when(this.pages.getDisplayTitle(PAGE, true)).thenReturn("Page title");

        assertEquals(List.of(Map.of("page", PAGE_KEY, "label", "", "folder", "", "order", 0, "panel", false,
            "viewable", true, "exists", true, "url", URL, "title", "Page title")), view(bookmark(PAGE_KEY, "")));
    }

    @Test
    void doesNotComputeTheTitleOfABookmarkWithLabel()
    {
        assertEquals(List.of(Map.of("page", PAGE_KEY, "label", "Label", "folder", "", "order", 0, "panel", false,
            "viewable", true, "exists", true, "url", URL)), view(bookmark(PAGE_KEY, "Label")));

        verify(this.pages, never()).getDisplayTitle(any(), anyBoolean());
    }

    @Test
    void revealsNothingAboutAPageTheCurrentUserCannotView()
    {
        when(this.rights.canView(PAGE)).thenReturn(false);

        assertEquals(List.of(Map.of("page", PAGE_KEY, "label", "", "folder", "", "order", 0, "panel", false,
            "viewable", false)), view(bookmark(PAGE_KEY, "")));

        verifyNoInteractions(this.pages);
    }

    private List<Map<String, Object>> view(BookmarkEntry bookmark)
    {
        return this.views.getBookmarks(List.of(bookmark));
    }
}

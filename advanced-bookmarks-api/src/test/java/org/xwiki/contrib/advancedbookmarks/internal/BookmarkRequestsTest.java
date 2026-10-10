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

import java.io.ByteArrayInputStream;
import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.container.Container;
import org.xwiki.container.Request;
import org.xwiki.container.servlet.ServletRequest;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.csrf.CSRFToken;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the checks of the requests that modify bookmarks.
 *
 * @version $Id$
 */
@ComponentTest
class BookmarkRequestsTest
{
    private static final String TOKEN = "token";

    private static final String FILE = "file";

    @InjectMockComponents
    private BookmarkRequests requests;

    @MockComponent
    private Container container;

    @MockComponent
    private CSRFToken csrfToken;

    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp()
    {
        this.httpRequest = mock(HttpServletRequest.class);
        ServletRequest servletRequest = mock(ServletRequest.class);
        when(servletRequest.getRequest()).thenReturn(this.httpRequest);
        when(this.container.getRequest()).thenReturn(servletRequest);
        when(this.httpRequest.getParameter(BookmarkRequests.TOKEN_PARAMETER)).thenReturn(TOKEN);
    }

    @Test
    void acceptsOnlyPostRequests()
    {
        when(this.httpRequest.getMethod()).thenReturn("post");
        assertTrue(this.requests.isPost());

        when(this.httpRequest.getMethod()).thenReturn("GET");
        assertFalse(this.requests.isPost());
    }

    @Test
    void checksTheCsrfTokenOfTheRequest()
    {
        when(this.csrfToken.isTokenValid(TOKEN)).thenReturn(true);
        assertTrue(this.requests.hasValidToken());

        when(this.csrfToken.isTokenValid(TOKEN)).thenReturn(false);
        assertFalse(this.requests.hasValidToken());
    }

    @Test
    void readsARequestParameter()
    {
        when(this.httpRequest.getParameter("user")).thenReturn("XWiki.bob");

        assertEquals("XWiki.bob", this.requests.getParameter("user"));
    }

    @Test
    void readsAnUploadedFile() throws Exception
    {
        Part part = mockPart(3, new byte[] { 1, 2, 3 });
        when(this.httpRequest.getPart(FILE)).thenReturn(part);

        assertArrayEquals(new byte[] { 1, 2, 3 }, this.requests.readFile(FILE, 3));
    }

    @Test
    void refusesAMissingOrTooLargeFile() throws Exception
    {
        assertThrows(BookmarkInvalidDataException.class, () -> this.requests.readFile(FILE, 3));

        Part declaredTooLarge = mockPart(4, new byte[0]);
        when(this.httpRequest.getPart(FILE)).thenReturn(declaredTooLarge);
        assertThrows(BookmarkInvalidDataException.class, () -> this.requests.readFile(FILE, 3));

        // The declared size can be wrong: the content itself is bounded.
        Part actuallyTooLarge = mockPart(1, new byte[] { 1, 2, 3, 4 });
        when(this.httpRequest.getPart(FILE)).thenReturn(actuallyTooLarge);
        assertThrows(BookmarkInvalidDataException.class, () -> this.requests.readFile(FILE, 3));
    }

    @Test
    void refusesARequestThatIsNotAReadableMultipartRequest() throws Exception
    {
        when(this.httpRequest.getPart(FILE)).thenThrow(new ServletException("not multipart"));
        assertThrows(BookmarkInvalidDataException.class, () -> this.requests.readFile(FILE, 3));

        Part part = mock(Part.class);
        when(part.getInputStream()).thenThrow(new IOException("broken"));
        doReturn(part).when(this.httpRequest).getPart(FILE);
        assertThrows(BookmarkInvalidDataException.class, () -> this.requests.readFile(FILE, 3));

        when(this.container.getRequest()).thenReturn(null);
        assertThrows(BookmarkInvalidDataException.class, () -> this.requests.readFile(FILE, 3));
    }

    @Test
    void parsesTheImportMode() throws Exception
    {
        assertTrue(BookmarkRequests.isReplaceMode("replace"));
        assertFalse(BookmarkRequests.isReplaceMode("merge"));
        assertThrows(BookmarkInvalidDataException.class, () -> BookmarkRequests.isReplaceMode("other"));
        assertThrows(BookmarkInvalidDataException.class, () -> BookmarkRequests.isReplaceMode(null));
    }

    @Test
    void refusesEverythingOutsideOfAnHttpRequest()
    {
        when(this.container.getRequest()).thenReturn(null);
        assertFalse(this.requests.isPost());
        assertFalse(this.requests.hasValidToken());
        assertNull(this.requests.getParameter("user"));

        when(this.container.getRequest()).thenReturn(mock(Request.class));
        assertFalse(this.requests.isPost());
    }

    private static Part mockPart(long size, byte[] content) throws IOException
    {
        Part part = mock(Part.class);
        when(part.getSize()).thenReturn(size);
        when(part.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        return part;
    }
}

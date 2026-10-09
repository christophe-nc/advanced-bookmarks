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

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.container.Container;
import org.xwiki.container.Request;
import org.xwiki.container.servlet.ServletRequest;
import org.xwiki.csrf.CSRFToken;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void refusesEverythingOutsideOfAnHttpRequest()
    {
        when(this.container.getRequest()).thenReturn(null);
        assertFalse(this.requests.isPost());
        assertFalse(this.requests.hasValidToken());

        when(this.container.getRequest()).thenReturn(mock(Request.class));
        assertFalse(this.requests.isPost());
    }
}

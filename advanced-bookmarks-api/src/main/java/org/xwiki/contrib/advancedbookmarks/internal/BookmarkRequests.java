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

import javax.inject.Inject;
import javax.inject.Singleton;

import jakarta.servlet.http.HttpServletRequest;

import org.xwiki.component.annotation.Component;
import org.xwiki.container.Container;
import org.xwiki.container.Request;
import org.xwiki.container.servlet.ServletRequest;
import org.xwiki.csrf.CSRFToken;

/**
 * Checks that the current HTTP request may modify bookmarks: writes are only accepted in POST requests carrying a
 * valid CSRF token.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkRequests.class)
@Singleton
public class BookmarkRequests
{
    /** The request parameter holding the CSRF token, as for the other XWiki forms. */
    public static final String TOKEN_PARAMETER = "form_token";

    @Inject
    private Container container;

    @Inject
    private CSRFToken csrfToken;

    /**
     * @return {@code true} if the current request is an HTTP POST request
     */
    public boolean isPost()
    {
        HttpServletRequest request = getHttpRequest();
        return request != null && "POST".equalsIgnoreCase(request.getMethod());
    }

    /**
     * @return {@code true} if the current request carries the valid CSRF token of the current user
     */
    public boolean hasValidToken()
    {
        HttpServletRequest request = getHttpRequest();
        return request != null && this.csrfToken.isTokenValid(request.getParameter(TOKEN_PARAMETER));
    }

    private HttpServletRequest getHttpRequest()
    {
        Request request = this.container.getRequest();
        return request instanceof ServletRequest servletRequest ? servletRequest.getRequest() : null;
    }
}

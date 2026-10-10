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
package org.xwiki.contrib.advancedbookmarks.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkException;
import org.xwiki.contrib.advancedbookmarks.BookmarkFolder;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkManager;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkJsonCodec;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkPages;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkReferences;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkRequests;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkRights;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.script.service.ScriptService;

/**
 * Exposes bookmarks to scripts as maps ready to be serialized as JSON. Every result has a {@code status} key holding
 * an HTTP status code and, on failure, an {@code error} key holding a code without any detail: {@code forbidden},
 * {@code invalidToken}, {@code methodNotAllowed}, {@code invalid}, {@code conflict} or {@code storageError}.
 * Details are logged on the server.
 * <p>
 * Write operations only accept POST requests carrying a valid CSRF token in the {@code form_token} parameter.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("advancedbookmarks")
@Singleton
public class AdvancedBookmarksScriptService implements ScriptService
{
    private static final String STATUS = "status";

    private static final String ERROR = "error";

    private static final String VERSION = "version";

    private static final String BOOKMARKS = "bookmarks";

    private static final String ORDER = "order";

    private static final int OK = 200;

    private static final int BAD_REQUEST = 400;

    private static final int FORBIDDEN = 403;

    private static final int METHOD_NOT_ALLOWED = 405;

    private static final int CONFLICT = 409;

    private static final int SERVER_ERROR = 500;

    @Inject
    private BookmarkManager bookmarkManager;

    @Inject
    private BookmarkRights rights;

    @Inject
    private BookmarkPages pages;

    @Inject
    private BookmarkJsonCodec jsonCodec;

    @Inject
    private BookmarkRequests requests;

    @Inject
    private BookmarkReferences references;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private Logger logger;

    /**
     * An operation on bookmarks, whose failures are translated into error results.
     */
    @FunctionalInterface
    private interface Operation
    {
        Map<String, Object> run() throws BookmarkException;
    }

    /**
     * Read all the bookmarks of a user, in display order.
     *
     * @param user the owner of the bookmarks, such as {@code XWiki.jdoe} or {@code jdoe}; empty for the current user
     * @return {@code user}, {@code version}, {@code folders} and {@code bookmarks}; each bookmark has the {@code page},
     *         {@code label}, {@code folder}, {@code order}, {@code panel} and {@code viewable} keys and, when the
     *         current user may view the page, {@code exists} and {@code url}
     */
    public Map<String, Object> getBookmarks(String user)
    {
        return execute(() -> {
            DocumentReference owner = this.references.resolveUser(user);
            BookmarkData data = this.bookmarkManager.getBookmarks(owner);
            Map<String, Object> result = success();
            result.put("user", this.serializer.serialize(owner));
            result.put(VERSION, String.valueOf(data.getVersion()));
            result.put("folders", data.getFolders().stream().map(AdvancedBookmarksScriptService::toMap).toList());
            result.put(BOOKMARKS, toMaps(this.bookmarkManager.getDisplayOrder(data)));
            return result;
        });
    }

    /**
     * @return the panel bookmarks of the current user, under the {@code bookmarks} key
     */
    public Map<String, Object> getPanel()
    {
        return execute(() -> {
            BookmarkData data = this.bookmarkManager.getBookmarks(this.rights.getCurrentUser());
            Map<String, Object> result = success();
            result.put(BOOKMARKS, toMaps(this.bookmarkManager.getPanel(data)));
            return result;
        });
    }

    /**
     * @param page the serialized reference of a page of the current wiki
     * @return whether the page is bookmarked by the current user, under the {@code bookmarked} key
     */
    public Map<String, Object> isBookmarked(String page)
    {
        return execute(() -> bookmarked(
            this.bookmarkManager.isBookmarked(this.rights.getCurrentUser(), this.references.resolvePage(page))));
    }

    /**
     * Add a page to the bookmarks of the current user, or remove it if it is already bookmarked.
     *
     * @param page the serialized reference of a page of the current wiki
     * @return whether the page is bookmarked after the operation, under the {@code bookmarked} key
     */
    public Map<String, Object> toggle(String page)
    {
        return executeWrite(() -> bookmarked(
            this.bookmarkManager.toggle(this.rights.getCurrentUser(), this.references.resolvePage(page))));
    }

    /**
     * Replace all the bookmarks of a user, provided they have not been modified since they were read.
     *
     * @param user the owner of the bookmarks; empty for the current user
     * @param json the folders and bookmarks, as a JSON object with the {@code folders} and {@code bookmarks} arrays
     * @param version the version the bookmarks were read at, as returned by {@link #getBookmarks(String)}; {@code 0}
     *            if there were none
     * @return the new version, under the {@code version} key
     */
    public Map<String, Object> save(String user, String json, String version)
    {
        return executeWrite(() -> {
            DocumentReference owner = this.references.resolveUser(user);
            BookmarkData data = this.jsonCodec.read(json);
            data.setVersion(parseVersion(version));
            Map<String, Object> result = success();
            result.put(VERSION, String.valueOf(this.bookmarkManager.save(owner, data)));
            return result;
        });
    }

    /**
     * Versions are exchanged as decimal strings: the versions of Solr exceed the integer precision of JavaScript
     * numbers (2^53), and a rounded version would always be reported as a conflict.
     */
    private static long parseVersion(String version) throws BookmarkInvalidDataException
    {
        try {
            long result = Long.parseLong(StringUtils.defaultString(version));
            if (result >= 0) {
                return result;
            }
        } catch (NumberFormatException e) {
            // Reported below as invalid data, without detail.
        }
        throw new BookmarkInvalidDataException(null);
    }

    private Map<String, Object> executeWrite(Operation operation)
    {
        if (!this.requests.isPost()) {
            return error(METHOD_NOT_ALLOWED, "methodNotAllowed");
        }
        if (!this.requests.hasValidToken()) {
            return error(FORBIDDEN, "invalidToken");
        }
        return execute(operation);
    }

    private Map<String, Object> execute(Operation operation)
    {
        try {
            return operation.run();
        } catch (BookmarkAccessDeniedException e) {
            return error(FORBIDDEN, "forbidden");
        } catch (BookmarkInvalidDataException e) {
            return error(BAD_REQUEST, "invalid");
        } catch (BookmarkStoreConflictException e) {
            return error(CONFLICT, "conflict");
        } catch (BookmarkException e) {
            this.logger.error("Advanced Bookmarks operation failed.", e);
            return error(SERVER_ERROR, "storageError");
        }
    }

    private List<Map<String, Object>> toMaps(List<BookmarkEntry> bookmarks)
    {
        List<Map<String, Object>> result = new ArrayList<>(bookmarks.size());
        for (BookmarkEntry bookmark : bookmarks) {
            result.add(toMap(bookmark));
        }
        return result;
    }

    /**
     * The page and label are the owner's own data and are always returned, so that saving what was read loses
     * nothing. Live information about the page is only returned if the current user may view it.
     */
    private Map<String, Object> toMap(BookmarkEntry bookmark)
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("page", bookmark.getPage());
        map.put("label", bookmark.getLabel());
        map.put("folder", bookmark.getFolder());
        map.put(ORDER, bookmark.getOrder());
        map.put("panel", bookmark.isPanel());
        DocumentReference page = this.resolver.resolve(bookmark.getPage());
        boolean viewable = this.rights.canView(page);
        map.put("viewable", viewable);
        if (viewable) {
            map.put("exists", this.pages.exists(page));
            map.put("url", this.pages.getURL(page));
        }
        return map;
    }

    private static Map<String, Object> toMap(BookmarkFolder folder)
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", folder.getId());
        map.put("name", folder.getName());
        map.put("parent", folder.getParent());
        map.put(ORDER, folder.getOrder());
        return map;
    }

    private static Map<String, Object> bookmarked(boolean bookmarked)
    {
        Map<String, Object> result = success();
        result.put("bookmarked", bookmarked);
        return result;
    }

    private static Map<String, Object> success()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, OK);
        return result;
    }

    private static Map<String, Object> error(int status, String code)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, status);
        result.put(ERROR, code);
        return result;
    }
}

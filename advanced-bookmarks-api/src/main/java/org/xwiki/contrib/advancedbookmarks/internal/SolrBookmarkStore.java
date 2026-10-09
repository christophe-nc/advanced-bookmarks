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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CursorMarkParams;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkRecord;
import org.xwiki.contrib.advancedbookmarks.BookmarkStore;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.search.solr.Solr;
import org.xwiki.search.solr.SolrException;
import org.xwiki.search.solr.XWikiSolrCore;

/**
 * Stores one versioned Solr document for each user and wiki.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class SolrBookmarkStore implements BookmarkStore
{
    static final String CORE_NAME = "advancedbookmarks";

    static final String WIKI_FIELD = "wiki";

    static final String USER_FIELD = "user";

    static final String DATA_FIELD = "data";

    static final String PAGES_FIELD = "pages";

    private static final String ID_FIELD = "id";

    private static final String VERSION_FIELD = "_version_";

    private static final String ALL_DOCUMENTS = "*:*";

    private static final String SEPARATOR = ":";

    private static final String DELETE_OPERATION = "delete";

    private static final String INVALID_VERSION = "Invalid bookmark version.";

    private static final String INVALID_DOCUMENT = "The bookmark document is invalid.";

    private static final int HTTP_CONFLICT = 409;

    private static final int MAX_IDENTITY_LENGTH = 512;

    private static final int QUERY_BATCH_SIZE = 500;

    private static final int MAX_QUERY_RESULTS = 100_000;

    private static final int MAX_REPLACE_ATTEMPTS = 3;

    @Inject
    private Solr solr;

    @Inject
    private BookmarkJsonCodec jsonCodec;

    @Inject
    private Logger logger;

    @Override
    public Optional<BookmarkRecord> get(String wiki, String user) throws BookmarkStoreException
    {
        validateIdentity(wiki, user);
        try {
            SolrDocumentList documents = queryById(getSolrClient(), wiki, user);
            if (documents.isEmpty()) {
                return Optional.empty();
            }
            if (documents.size() != 1) {
                throw new BookmarkStoreException("The bookmark document is not unique.", null);
            }
            SolrDocument document = documents.get(0);
            String json = stringValue(document, DATA_FIELD);
            Collection<Object> values = document.getFieldValues(PAGES_FIELD);
            Set<Object> storedPages = values == null ? Set.of() : Set.copyOf(values);
            if (!storedPages.equals(getStoredPageReferences(json))) {
                throw new BookmarkStoreException("The bookmark document is inconsistent.", null);
            }
            return Optional.of(new BookmarkRecord(json, numberValue(document, VERSION_FIELD)));
        } catch (SolrException | SolrServerException | IOException e) {
            throw storageFailure("read", e);
        }
    }

    @Override
    public long save(String wiki, String user, String json, long expectedVersion)
        throws BookmarkStoreConflictException, BookmarkStoreException
    {
        validateIdentity(wiki, user);
        validateVersion(expectedVersion);
        Set<String> pages = this.jsonCodec.getPageReferences(json);
        SolrInputDocument document = new SolrInputDocument();
        document.addField(ID_FIELD, documentId(wiki, user));
        document.addField(WIKI_FIELD, wiki);
        document.addField(USER_FIELD, user);
        document.addField(DATA_FIELD, json);
        document.addField(PAGES_FIELD, pages);
        document.addField(VERSION_FIELD, toSolrVersion(expectedVersion));

        try {
            SolrClient client = getSolrClient();
            client.add(document);
            client.commit();
            SolrDocumentList saved = queryById(client, wiki, user);
            if (saved.size() != 1) {
                throw new BookmarkStoreException("The saved bookmark document could not be verified.", null);
            }
            if (!json.equals(stringValue(saved.get(0), DATA_FIELD))) {
                throw new BookmarkStoreConflictException(null);
            }
            return numberValue(saved.get(0), VERSION_FIELD);
        } catch (SolrException | SolrServerException | IOException | org.apache.solr.common.SolrException e) {
            throw updateFailure("write", e);
        }
    }

    @Override
    public void delete(String wiki, String user, long expectedVersion)
        throws BookmarkStoreConflictException, BookmarkStoreException
    {
        validateIdentity(wiki, user);
        validateVersion(expectedVersion);
        try {
            SolrClient client = getSolrClient();
            UpdateRequest request = new UpdateRequest();
            request.deleteById(documentId(wiki, user), toSolrVersion(expectedVersion));
            request.process(client);
            client.commit();
        } catch (SolrException | SolrServerException | IOException | org.apache.solr.common.SolrException e) {
            throw updateFailure(DELETE_OPERATION, e);
        }
    }

    @Override
    public void deleteUser(String user) throws BookmarkStoreException
    {
        validateUser(user);
        deleteByFilter(USER_FIELD, user);
    }

    @Override
    public void deleteWiki(String wiki) throws BookmarkStoreException
    {
        validateWiki(wiki);
        deleteByFilter(WIKI_FIELD, wiki);
    }

    @Override
    public int replacePage(String wiki, String oldPage, String newPage)
        throws BookmarkStoreConflictException, BookmarkStoreException
    {
        validatePage(oldPage);
        validatePage(newPage);
        validateWiki(wiki);
        if (oldPage.equals(newPage)) {
            return 0;
        }

        int updated = 0;
        for (String user : findUsersByPage(wiki, oldPage)) {
            if (replacePage(wiki, user, oldPage, newPage)) {
                updated++;
            }
        }
        return updated;
    }

    @Override
    public List<String> findUsersByPage(String wiki, String page) throws BookmarkStoreException
    {
        validateWiki(wiki);
        validatePage(page);
        return queryUsers(wiki, PAGES_FIELD, page);
    }

    @Override
    public List<String> listUsers(String wiki) throws BookmarkStoreException
    {
        validateWiki(wiki);
        return queryUsers(wiki, null, null);
    }

    /**
     * Replace a page in the bookmarks of one user, re-reading the document after each version conflict.
     *
     * @return {@code true} if the document was updated
     */
    private boolean replacePage(String wiki, String user, String oldPage, String newPage)
        throws BookmarkStoreException
    {
        for (int attempt = 1;; attempt++) {
            Optional<BookmarkRecord> current = get(wiki, user);
            if (current.isEmpty() || !getStoredPageReferences(current.get().json()).contains(oldPage)) {
                // The page was removed from these bookmarks meanwhile: nothing left to replace.
                return false;
            }
            String updatedJson = this.jsonCodec.replacePage(current.get().json(), oldPage, newPage);
            try {
                save(wiki, user, updatedJson, current.get().version());
                return true;
            } catch (BookmarkStoreConflictException e) {
                if (attempt >= MAX_REPLACE_ATTEMPTS) {
                    throw e;
                }
                this.logger.debug("Retrying a bookmark page replacement after a version conflict.");
            }
        }
    }

    private void deleteByFilter(String field, String value) throws BookmarkStoreException
    {
        try {
            SolrClient client = getSolrClient();
            client.deleteByQuery(filter(field, value));
            client.commit();
        } catch (SolrException | SolrServerException | IOException | org.apache.solr.common.SolrException e) {
            throw storageFailure(DELETE_OPERATION, e);
        }
    }

    private List<String> queryUsers(String wiki, String field, String value) throws BookmarkStoreException
    {
        try {
            SolrClient client = getSolrClient();
            List<String> users = new ArrayList<>();
            String cursor;
            String nextCursor = CursorMarkParams.CURSOR_MARK_START;
            // Cursor paging: Solr returns the cursor it was given once all the results have been read.
            do {
                cursor = nextCursor;
                QueryResponse response = client.query(createUsersQuery(wiki, field, value, cursor));
                for (SolrDocument document : response.getResults()) {
                    users.add(stringValue(document, USER_FIELD));
                }
                if (users.size() >= MAX_QUERY_RESULTS) {
                    throw new BookmarkStoreException("The bookmark query result limit was reached.", null);
                }
                nextCursor = response.getNextCursorMark();
            } while (nextCursor != null && !nextCursor.equals(cursor));
            users.sort(String.CASE_INSENSITIVE_ORDER);
            return users;
        } catch (SolrException | SolrServerException | IOException | org.apache.solr.common.SolrException e) {
            throw storageFailure("query", e);
        }
    }

    private static SolrQuery createUsersQuery(String wiki, String field, String value, String cursor)
    {
        SolrQuery query = new SolrQuery(ALL_DOCUMENTS);
        query.addFilterQuery(filter(WIKI_FIELD, wiki));
        if (field != null) {
            query.addFilterQuery(filter(field, value));
        }
        query.setFields(USER_FIELD);
        query.setRows(QUERY_BATCH_SIZE);
        query.setSort(ID_FIELD, SolrQuery.ORDER.asc);
        query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursor);
        return query;
    }

    /**
     * Stored data is expected to be valid since it was validated when written: if it is not, the document is
     * corrupted, which is a storage failure rather than an invalid request.
     */
    private Set<String> getStoredPageReferences(String json) throws BookmarkStoreException
    {
        try {
            return this.jsonCodec.getPageReferences(json);
        } catch (BookmarkInvalidDataException e) {
            throw new BookmarkStoreException(INVALID_DOCUMENT, e);
        }
    }

    private static SolrDocumentList queryById(SolrClient client, String wiki, String user)
        throws SolrServerException, IOException
    {
        SolrQuery query = new SolrQuery(ALL_DOCUMENTS);
        query.addFilterQuery(filter(ID_FIELD, documentId(wiki, user)));
        query.setFields(DATA_FIELD, VERSION_FIELD, PAGES_FIELD);
        query.setRows(2);
        return client.query(query).getResults();
    }

    /**
     * XWiki returns a core without client, rather than failing, when it cannot create the core: for instance when
     * the core initializer is not visible from the root component manager, or when a remote Solr server lacks the
     * core. This must be reported as a storage failure instead of breaking the caller.
     */
    private SolrClient getSolrClient() throws SolrException, BookmarkStoreException
    {
        XWikiSolrCore core = this.solr.getCore(CORE_NAME);
        SolrClient client = core != null ? core.getClient() : null;
        if (client == null) {
            throw new BookmarkStoreException("The Solr core [" + CORE_NAME + "] is not available.", null);
        }
        return client;
    }

    /**
     * Length prefixes make the identifier unambiguous whatever characters the wiki and user identifiers contain.
     */
    private static String documentId(String wiki, String user)
    {
        return wiki.length() + SEPARATOR + wiki + user.length() + SEPARATOR + user;
    }

    private static String filter(String field, String value)
    {
        return field + SEPARATOR + ClientUtils.escapeQueryChars(value);
    }

    /**
     * Solr optimistic concurrency: a negative version requires that the document does not exist yet.
     */
    private static long toSolrVersion(long expectedVersion)
    {
        return expectedVersion == 0 ? -1L : expectedVersion;
    }

    private static String stringValue(SolrDocument document, String field) throws BookmarkStoreException
    {
        Object value = document.getFieldValue(field);
        if (!(value instanceof String stringValue)) {
            throw new BookmarkStoreException(INVALID_DOCUMENT, null);
        }
        return stringValue;
    }

    private static long numberValue(SolrDocument document, String field) throws BookmarkStoreException
    {
        Object value = document.getFieldValue(field);
        if (!(value instanceof Number number)) {
            throw new BookmarkStoreException(INVALID_DOCUMENT, null);
        }
        return number.longValue();
    }

    private static BookmarkStoreException updateFailure(String operation, Exception cause)
    {
        if (isConflict(cause)) {
            return new BookmarkStoreConflictException(cause);
        }
        return storageFailure(operation, cause);
    }

    private static boolean isConflict(Throwable exception)
    {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.apache.solr.common.SolrException solrException
                && solrException.code() == HTTP_CONFLICT) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static BookmarkStoreException storageFailure(String operation, Throwable cause)
    {
        return new BookmarkStoreException("Unable to " + operation + " bookmark data in Solr.", cause);
    }

    private static void validateVersion(long expectedVersion) throws BookmarkStoreException
    {
        if (expectedVersion < 0) {
            throw new BookmarkStoreException(INVALID_VERSION, null);
        }
    }

    private static void validateIdentity(String wiki, String user) throws BookmarkStoreException
    {
        validateWiki(wiki);
        validateUser(user);
    }

    private static void validateUser(String user) throws BookmarkStoreException
    {
        if (user == null || user.isBlank() || user.length() > MAX_IDENTITY_LENGTH) {
            throw new BookmarkStoreException("Invalid bookmark user reference.", null);
        }
    }

    private static void validateWiki(String wiki) throws BookmarkStoreException
    {
        if (wiki == null || wiki.isBlank() || wiki.length() > MAX_IDENTITY_LENGTH) {
            throw new BookmarkStoreException("Invalid wiki identifier.", null);
        }
    }

    private static void validatePage(String page) throws BookmarkStoreException
    {
        if (page == null || page.isBlank() || page.length() > BookmarkJsonCodec.MAX_PAGE_REFERENCE_LENGTH) {
            throw new BookmarkStoreException("Invalid bookmark page reference.", null);
        }
    }
}

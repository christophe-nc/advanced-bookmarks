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
import java.util.Comparator;
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
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
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
    private static final int MAX_IDENTITY_LENGTH = 512;
    private static final int QUERY_BATCH_SIZE = 500;
    private static final int MAX_QUERY_RESULTS = 100_000;
    private static final int MAX_REPLACE_ATTEMPTS = 3;

    @Inject
    private Solr solr;

    @Inject
    private Logger logger;

    private final BookmarkJsonCodec jsonCodec = new BookmarkJsonCodec();

    @Override
    public Optional<BookmarkRecord> get(String wiki, String user) throws BookmarkStoreException
    {
        validateIdentity(wiki, user);
        try {
            SolrDocumentList documents = queryById(wiki, user);
            if (documents.isEmpty()) {
                return Optional.empty();
            }
            if (documents.size() != 1) {
                throw new BookmarkStoreException("The bookmark document is not unique.", null);
            }
            SolrDocument document = documents.get(0);
            String json = stringValue(document, DATA_FIELD);
            Set<String> expectedPages = jsonCodec.getPageReferences(json);
            Collection<Object> values = document.getFieldValues(PAGES_FIELD);
            Set<Object> storedPages = values == null ? Set.of() : Set.copyOf(values);
            if (!storedPages.equals(expectedPages)) {
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
        if (expectedVersion < 0) {
            throw new BookmarkStoreException("Invalid bookmark version.", null);
        }
        Set<String> pages = jsonCodec.getPageReferences(json);
        SolrInputDocument document = new SolrInputDocument();
        document.addField(ID_FIELD, documentId(wiki, user));
        document.addField(WIKI_FIELD, wiki);
        document.addField(USER_FIELD, user);
        document.addField(DATA_FIELD, json);
        document.addField(PAGES_FIELD, pages);
        document.addField(VERSION_FIELD, expectedVersion == 0 ? -1L : expectedVersion);

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
        } catch (org.apache.solr.common.SolrException e) {
            if (e.code() == 409) {
                throw new BookmarkStoreConflictException(e);
            }
            throw storageFailure("write", e);
        } catch (SolrException | SolrServerException | IOException e) {
            if (isConflict(e)) {
                throw new BookmarkStoreConflictException(e);
            }
            throw storageFailure("write", e);
        }
    }

    @Override
    public void delete(String wiki, String user, long expectedVersion)
        throws BookmarkStoreConflictException, BookmarkStoreException
    {
        validateIdentity(wiki, user);
        if (expectedVersion < 0) {
            throw new BookmarkStoreException("Invalid bookmark version.", null);
        }
        try {
            SolrClient client = getSolrClient();
            UpdateRequest request = new UpdateRequest();
            request.deleteById(documentId(wiki, user), expectedVersion == 0 ? -1L : expectedVersion);
            request.process(client);
            client.commit();
        } catch (org.apache.solr.common.SolrException e) {
            if (e.code() == 409) {
                throw new BookmarkStoreConflictException(e);
            }
            throw storageFailure("delete", e);
        } catch (SolrException | SolrServerException | IOException e) {
            if (isConflict(e)) {
                throw new BookmarkStoreConflictException(e);
            }
            throw storageFailure("delete", e);
        }
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

        List<String> users = findUsersByPage(wiki, oldPage);
        int updated = 0;
        for (String user : users) {
            boolean replaced = false;
            for (int attempt = 0; attempt < MAX_REPLACE_ATTEMPTS && !replaced; attempt++) {
                Optional<BookmarkRecord> current = get(wiki, user);
                if (current.isEmpty() || !jsonCodec.getPageReferences(current.get().json()).contains(oldPage)) {
                    replaced = true;
                    continue;
                }
                String updatedJson = jsonCodec.replacePage(current.get().json(), oldPage, newPage);
                try {
                    save(wiki, user, updatedJson, current.get().version());
                    updated++;
                    replaced = true;
                } catch (BookmarkStoreConflictException e) {
                    if (attempt == MAX_REPLACE_ATTEMPTS - 1) {
                        throw e;
                    }
                    logger.debug("Retrying a bookmark page replacement after a version conflict.");
                }
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

    private List<String> queryUsers(String wiki, String field, String value) throws BookmarkStoreException
    {
        try {
            SolrClient client = getSolrClient();
            List<String> users = new ArrayList<>();
            String cursor = "*";
            while (users.size() < MAX_QUERY_RESULTS) {
                SolrQuery query = new SolrQuery("*:*");
                query.addFilterQuery(filter(WIKI_FIELD, wiki));
                if (field != null) {
                    query.addFilterQuery(filter(field, value));
                }
                query.setFields(USER_FIELD);
                query.setRows(QUERY_BATCH_SIZE);
                query.setSort(ID_FIELD, SolrQuery.ORDER.asc);
                query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursor);

                QueryResponse response = client.query(query);
                SolrDocumentList documents = response.getResults();
                for (SolrDocument document : documents) {
                    users.add(stringValue(document, USER_FIELD));
                    if (users.size() == MAX_QUERY_RESULTS) {
                        break;
                    }
                }
                String nextCursor = response.getNextCursorMark();
                if (nextCursor == null || nextCursor.equals(cursor) || documents.isEmpty()) {
                    break;
                }
                cursor = nextCursor;
            }
            if (users.size() == MAX_QUERY_RESULTS) {
                throw new BookmarkStoreException("The bookmark query result limit was reached.", null);
            }
            users.sort(String.CASE_INSENSITIVE_ORDER);
            return users;
        } catch (SolrException | SolrServerException | IOException e) {
            throw storageFailure("query", e);
        }
    }

    private SolrDocumentList queryById(String wiki, String user)
        throws SolrException, SolrServerException, IOException
    {
        return queryById(getSolrClient(), wiki, user);
    }

    private SolrDocumentList queryById(SolrClient client, String wiki, String user)
        throws SolrServerException, IOException
    {
        SolrQuery query = new SolrQuery("*:*");
        query.addFilterQuery(filter(ID_FIELD, documentId(wiki, user)));
        query.setFields(DATA_FIELD, VERSION_FIELD);
        query.addField(PAGES_FIELD);
        query.setRows(2);
        return client.query(query).getResults();
    }

    private SolrClient getSolrClient() throws SolrException
    {
        XWikiSolrCore core = solr.getCore(CORE_NAME);
        return core.getClient();
    }

    private static String documentId(String wiki, String user)
    {
        return wiki.length() + ":" + wiki + user.length() + ":" + user;
    }

    private static String filter(String field, String value)
    {
        return field + ":" + ClientUtils.escapeQueryChars(value);
    }

    private static String stringValue(SolrDocument document, String field) throws BookmarkStoreException
    {
        Object value = document.getFieldValue(field);
        if (!(value instanceof String stringValue)) {
            throw new BookmarkStoreException("The bookmark document is invalid.", null);
        }
        return stringValue;
    }

    private static long numberValue(SolrDocument document, String field) throws BookmarkStoreException
    {
        Object value = document.getFieldValue(field);
        if (!(value instanceof Number number)) {
            throw new BookmarkStoreException("The bookmark document is invalid.", null);
        }
        return number.longValue();
    }

    private static boolean isConflict(Throwable exception)
    {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.apache.solr.common.SolrException solrException
                && solrException.code() == 409) {
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

    private static void validateIdentity(String wiki, String user) throws BookmarkStoreException
    {
        validateWiki(wiki);
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

    private static final class CursorMarkParams
    {
        private static final String CURSOR_MARK_PARAM = "cursorMark";

        private CursorMarkParams()
        {
        }
    }
}

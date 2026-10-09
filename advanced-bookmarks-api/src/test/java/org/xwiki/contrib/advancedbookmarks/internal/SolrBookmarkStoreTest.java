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
import java.util.List;
import java.util.Optional;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkRecord;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.search.solr.Solr;
import org.xwiki.search.solr.SolrException;
import org.xwiki.search.solr.XWikiSolrCore;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the Solr storage component with a mocked Solr client.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(BookmarkJsonCodec.class)
class SolrBookmarkStoreTest
{
    private static final String WIKI = "wiki";

    private static final String USER = "xwiki:XWiki.User";

    private static final String VERSION = "_version_";

    private static final String OLD_PAGE = "wiki:Old.Page";

    private static final String NEW_PAGE = "wiki:New.Page";

    private static final String BOOKMARK_JSON =
        "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:Space.Page\",\"label\":\"Page\"}]}";

    private static final String OLD_JSON = "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:Old.Page\"}]}";

    private static final String NEW_JSON = "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:New.Page\"}]}";

    @InjectMockComponents
    private SolrBookmarkStore store;

    @MockComponent
    private Solr solr;

    private SolrClient client;

    @BeforeEach
    void setUp() throws Exception
    {
        this.client = mock(SolrClient.class);
        XWikiSolrCore core = mock(XWikiSolrCore.class);
        when(this.solr.getCore(SolrBookmarkStore.CORE_NAME)).thenReturn(core);
        when(core.getClient()).thenReturn(this.client);
    }

    @Test
    void readsNothingWhenNoDocumentIsStored() throws Exception
    {
        QueryResponse empty = responseWith();
        when(this.client.query(any(SolrQuery.class))).thenReturn(empty);

        assertEquals(Optional.empty(), this.store.get(WIKI, USER));
    }

    @Test
    void readsTheStoredJsonAndVersion() throws Exception
    {
        QueryResponse response = responseWith(bookmarkDocument(BOOKMARK_JSON, 7L, "wiki:Space.Page"));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertEquals(Optional.of(new BookmarkRecord(BOOKMARK_JSON, 7L)), this.store.get(WIKI, USER));
    }

    @Test
    void readsADocumentWithoutBookmarks() throws Exception
    {
        String json = "{\"folders\":[],\"bookmarks\":[]}";
        QueryResponse response = responseWith(bookmarkDocument(json, 2L));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertEquals(Optional.of(new BookmarkRecord(json, 2L)), this.store.get(WIKI, USER));
    }

    @Test
    void rejectsADocumentWithInvalidFields() throws Exception
    {
        SolrDocument withoutData = new SolrDocument();
        withoutData.setField(VERSION, 2L);
        SolrDocument withoutVersion = bookmarkDocument(BOOKMARK_JSON, 0L, "wiki:Space.Page");
        withoutVersion.setField(VERSION, "2");
        QueryResponse first = responseWith(withoutData);
        QueryResponse second = responseWith(withoutVersion);
        when(this.client.query(any(SolrQuery.class))).thenReturn(first, second);

        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
    }

    @Test
    void rejectsADocumentWhosePagesDoNotMatchItsJson() throws Exception
    {
        QueryResponse response = responseWith(bookmarkDocument(BOOKMARK_JSON, 7L, "wiki:Other.Page"));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
    }

    @Test
    void reportsCorruptedStoredJsonAsAStorageFailure() throws Exception
    {
        QueryResponse response = responseWith(bookmarkDocument("{", 7L, "wiki:Space.Page"));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        BookmarkStoreException exception =
            assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
        assertFalse(exception instanceof BookmarkInvalidDataException);
    }

    @Test
    void rejectsDuplicateDocuments() throws Exception
    {
        QueryResponse response = responseWith(bookmarkDocument(BOOKMARK_JSON, 7L, "wiki:Space.Page"),
            bookmarkDocument(BOOKMARK_JSON, 8L, "wiki:Space.Page"));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
    }

    @Test
    void wrapsSolrFailures() throws Exception
    {
        when(this.client.query(any(SolrQuery.class))).thenThrow(new IOException("down"));

        BookmarkStoreException exception =
            assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void wrapsAnUnavailableCore() throws Exception
    {
        when(this.solr.getCore(SolrBookmarkStore.CORE_NAME)).thenThrow(new SolrException("missing core"));

        assertThrows(BookmarkStoreException.class, () -> this.store.listUsers(WIKI));
    }

    @Test
    void reportsACoreThatXWikiCouldNotCreateAsAStorageFailure() throws Exception
    {
        XWikiSolrCore coreWithoutClient = mock(XWikiSolrCore.class);
        when(this.solr.getCore(SolrBookmarkStore.CORE_NAME)).thenReturn(coreWithoutClient, (XWikiSolrCore) null);

        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
        assertThrows(BookmarkStoreException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, 0L));
    }

    @Test
    void rejectsInvalidIdentities()
    {
        assertThrows(BookmarkStoreException.class, () -> this.store.get(" ", USER));
        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, null));
        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, "u".repeat(513)));
        assertThrows(BookmarkStoreException.class, () -> this.store.deleteUser(""));
        assertThrows(BookmarkStoreException.class, () -> this.store.deleteWiki(null));
        assertThrows(BookmarkStoreException.class, () -> this.store.findUsersByPage(WIKI, ""));

        verifyNoInteractions(this.client);
    }

    @Test
    void savesJsonAndIndexesItsPageReferences() throws Exception
    {
        QueryResponse response = responseWith(bookmarkDocument(BOOKMARK_JSON, 4L, "wiki:Space.Page"));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        long version = this.store.save(WIKI, USER, BOOKMARK_JSON, 3L);

        assertEquals(4L, version);
        ArgumentCaptor<SolrInputDocument> documentCaptor = ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(this.client).add(documentCaptor.capture());
        SolrInputDocument saved = documentCaptor.getValue();
        assertEquals(WIKI, saved.getFieldValue(SolrBookmarkStore.WIKI_FIELD));
        assertEquals(USER, saved.getFieldValue(SolrBookmarkStore.USER_FIELD));
        assertEquals("wiki:Space.Page", saved.getFieldValue(SolrBookmarkStore.PAGES_FIELD));
        assertEquals(3L, saved.getFieldValue(VERSION));
        verify(this.client).commit();
    }

    @Test
    void requiresThatANewDocumentDoesNotExistYet() throws Exception
    {
        QueryResponse response = responseWith(bookmarkDocument(BOOKMARK_JSON, 1L, "wiki:Space.Page"));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        this.store.save(WIKI, USER, BOOKMARK_JSON, 0L);

        ArgumentCaptor<SolrInputDocument> documentCaptor = ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(this.client).add(documentCaptor.capture());
        assertEquals(-1L, documentCaptor.getValue().getFieldValue(VERSION));
    }

    @Test
    void rejectsInvalidJsonOrVersionBeforeCallingSolr()
    {
        assertThrows(BookmarkInvalidDataException.class, () -> this.store.save(WIKI, USER, "{}", 0L));
        assertThrows(BookmarkStoreException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, -1L));
        assertThrows(BookmarkStoreException.class, () -> this.store.delete(WIKI, USER, -1L));

        verifyNoInteractions(this.client);
    }

    @Test
    void reportsAConflictIfAnotherWriteReplacesTheDataBeforeVerification() throws Exception
    {
        QueryResponse response = responseWith(bookmarkDocument("{\"folders\":[],\"bookmarks\":[]}", 5L));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertThrows(BookmarkStoreConflictException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, 3L));
    }

    @Test
    void reportsAFailureIfTheSavedDocumentCannotBeFound() throws Exception
    {
        QueryResponse empty = responseWith();
        when(this.client.query(any(SolrQuery.class))).thenReturn(empty);

        BookmarkStoreException exception =
            assertThrows(BookmarkStoreException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, 3L));
        assertFalse(exception instanceof BookmarkStoreConflictException);
    }

    @Test
    void wrapsWriteFailures() throws Exception
    {
        when(this.client.add(any(SolrInputDocument.class))).thenThrow(new IOException("down"));

        BookmarkStoreException exception =
            assertThrows(BookmarkStoreException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, 3L));
        assertFalse(exception instanceof BookmarkStoreConflictException);
    }

    @Test
    void mapsTheNativeSolrVersionConflict() throws Exception
    {
        when(this.client.add(any(SolrInputDocument.class))).thenThrow(versionConflict());

        assertThrows(BookmarkStoreConflictException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, 3L));
    }

    @Test
    void mapsAWrappedSolrVersionConflict() throws Exception
    {
        when(this.client.add(any(SolrInputDocument.class)))
            .thenThrow(new org.apache.solr.client.solrj.SolrServerException(versionConflict()));

        assertThrows(BookmarkStoreConflictException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, 3L));
    }

    @Test
    void deletesConditionallyOnTheVersion() throws Exception
    {
        this.store.delete(WIKI, USER, 5L);

        verify(this.client).request(any(SolrRequest.class), any());
        verify(this.client).commit();
    }

    @Test
    void mapsADeleteVersionConflict() throws Exception
    {
        when(this.client.request(any(SolrRequest.class), any())).thenThrow(versionConflict());

        assertThrows(BookmarkStoreConflictException.class, () -> this.store.delete(WIKI, USER, 5L));
    }

    @Test
    void deletesAUserInEveryWiki() throws Exception
    {
        this.store.deleteUser(USER);

        verify(this.client).deleteByQuery("user:xwiki\\:XWiki.User");
        verify(this.client).commit();
    }

    @Test
    void deletesAWiki() throws Exception
    {
        this.store.deleteWiki(WIKI);

        verify(this.client).deleteByQuery("wiki:wiki");
        verify(this.client).commit();
    }

    @Test
    void wrapsDeletionFailures() throws Exception
    {
        when(this.client.deleteByQuery(anyString())).thenThrow(new IOException("down"));

        assertThrows(BookmarkStoreException.class, () -> this.store.deleteWiki(WIKI));
    }

    @Test
    void listsUsersOverSeveralPagesInAlphabeticalOrder() throws Exception
    {
        QueryResponse first = responseWith(userDocument("xwiki:XWiki.Zoe"));
        when(first.getNextCursorMark()).thenReturn("next");
        QueryResponse second = responseWith(userDocument("xwiki:XWiki.adam"));
        when(second.getNextCursorMark()).thenReturn("next");
        when(this.client.query(any(SolrQuery.class))).thenReturn(first, second);

        assertEquals(List.of("xwiki:XWiki.adam", "xwiki:XWiki.Zoe"), this.store.listUsers(WIKI));
    }

    @Test
    void refusesToReturnTooManyUsers() throws Exception
    {
        SolrDocument[] page = new SolrDocument[500];
        java.util.Arrays.fill(page, userDocument(USER));
        QueryResponse response = responseWith(page);
        int[] calls = {0};
        when(response.getNextCursorMark()).thenAnswer(invocation -> "cursor" + calls[0]++);
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertThrows(BookmarkStoreException.class, () -> this.store.listUsers(WIKI));
    }

    @Test
    void findsUsersByPageWithinTheWiki() throws Exception
    {
        QueryResponse response = responseWith(userDocument(USER));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertEquals(List.of(USER), this.store.findUsersByPage(WIKI, OLD_PAGE));

        ArgumentCaptor<SolrQuery> queryCaptor = ArgumentCaptor.forClass(SolrQuery.class);
        verify(this.client).query(queryCaptor.capture());
        assertEquals(List.of("wiki:wiki", "pages:wiki\\:Old.Page"), List.of(queryCaptor.getValue().getFilterQueries()));
    }

    @Test
    void replacesPageReferencesAndUpdatesTheSolrIndex() throws Exception
    {
        QueryResponse usersResponse = responseWith(userDocument(USER));
        QueryResponse currentResponse = responseWith(bookmarkDocument(OLD_JSON, 3L, OLD_PAGE));
        QueryResponse savedResponse = responseWith(bookmarkDocument(NEW_JSON, 4L, NEW_PAGE));
        when(this.client.query(any(SolrQuery.class))).thenReturn(usersResponse, currentResponse, savedResponse);

        assertEquals(1, this.store.replacePage(WIKI, OLD_PAGE, NEW_PAGE));

        ArgumentCaptor<SolrInputDocument> documentCaptor = ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(this.client).add(documentCaptor.capture());
        assertEquals(NEW_JSON, documentCaptor.getValue().getFieldValue(SolrBookmarkStore.DATA_FIELD));
        assertEquals(NEW_PAGE, documentCaptor.getValue().getFieldValue(SolrBookmarkStore.PAGES_FIELD));
    }

    @Test
    void retriesAPageReplacementAfterAConflict() throws Exception
    {
        QueryResponse usersResponse = responseWith(userDocument(USER));
        QueryResponse currentResponse = responseWith(bookmarkDocument(OLD_JSON, 3L, OLD_PAGE));
        QueryResponse savedResponse = responseWith(bookmarkDocument(NEW_JSON, 5L, NEW_PAGE));
        when(this.client.query(any(SolrQuery.class)))
            .thenReturn(usersResponse, currentResponse, currentResponse, savedResponse);
        when(this.client.add(any(SolrInputDocument.class))).thenThrow(versionConflict()).thenReturn(null);

        assertEquals(1, this.store.replacePage(WIKI, OLD_PAGE, NEW_PAGE));

        verify(this.client, times(2)).add(any(SolrInputDocument.class));
    }

    @Test
    void givesUpAPageReplacementAfterThreeConflicts() throws Exception
    {
        QueryResponse usersResponse = responseWith(userDocument(USER));
        QueryResponse currentResponse = responseWith(bookmarkDocument(OLD_JSON, 3L, OLD_PAGE));
        when(this.client.query(any(SolrQuery.class))).thenReturn(usersResponse, currentResponse);
        when(this.client.add(any(SolrInputDocument.class))).thenThrow(versionConflict());

        assertThrows(BookmarkStoreConflictException.class, () -> this.store.replacePage(WIKI, OLD_PAGE, NEW_PAGE));

        verify(this.client, times(3)).add(any(SolrInputDocument.class));
    }

    @Test
    void skipsUsersWhoNoLongerHaveTheRenamedPage() throws Exception
    {
        QueryResponse usersResponse = responseWith(userDocument(USER));
        QueryResponse currentResponse = responseWith(bookmarkDocument(NEW_JSON, 3L, NEW_PAGE));
        when(this.client.query(any(SolrQuery.class))).thenReturn(usersResponse, currentResponse);

        assertEquals(0, this.store.replacePage(WIKI, OLD_PAGE, NEW_PAGE));

        verify(this.client, never()).add(any(SolrInputDocument.class));
    }

    @Test
    void ignoresARenameToTheSamePage() throws Exception
    {
        assertEquals(0, this.store.replacePage(WIKI, OLD_PAGE, OLD_PAGE));

        verifyNoInteractions(this.client);
    }

    private static org.apache.solr.common.SolrException versionConflict()
    {
        return new org.apache.solr.common.SolrException(org.apache.solr.common.SolrException.ErrorCode.CONFLICT,
            "version conflict");
    }

    private static QueryResponse responseWith(SolrDocument... sourceDocuments)
    {
        SolrDocumentList documents = new SolrDocumentList();
        documents.addAll(List.of(sourceDocuments));
        QueryResponse response = mock(QueryResponse.class);
        when(response.getResults()).thenReturn(documents);
        return response;
    }

    private static SolrDocument userDocument(String user)
    {
        SolrDocument document = new SolrDocument();
        document.setField(SolrBookmarkStore.USER_FIELD, user);
        return document;
    }

    private static SolrDocument bookmarkDocument(String json, long version, String... pages)
    {
        SolrDocument document = new SolrDocument();
        document.setField(SolrBookmarkStore.DATA_FIELD, json);
        document.setField(VERSION, version);
        for (String page : pages) {
            document.addField(SolrBookmarkStore.PAGES_FIELD, page);
        }
        return document;
    }
}

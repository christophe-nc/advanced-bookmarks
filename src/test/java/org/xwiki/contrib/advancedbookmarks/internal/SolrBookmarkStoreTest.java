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

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.search.solr.Solr;
import org.xwiki.search.solr.XWikiSolrCore;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the Solr storage component with a mocked Solr client.
 *
 * @version $Id$
 */
@ComponentTest
class SolrBookmarkStoreTest
{
    private static final String BOOKMARK_JSON =
        "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:Space.Page\",\"label\":\"Page\"}]}";

    @InjectMockComponents
    private SolrBookmarkStore store;

    @MockComponent
    private Solr solr;

    private SolrClient client;

    private XWikiSolrCore core;

    @BeforeEach
    void setUp() throws Exception
    {
        client = mock(SolrClient.class);
        core = mock(XWikiSolrCore.class);
        when(solr.getCore(SolrBookmarkStore.CORE_NAME)).thenReturn(core);
        when(core.getClient()).thenReturn(client);
    }

    @Test
    void savesJsonAndIndexesItsPageReferences() throws Exception
    {
        SolrDocument storedDocument = new SolrDocument();
        storedDocument.setField(SolrBookmarkStore.DATA_FIELD, BOOKMARK_JSON);
        storedDocument.setField("_version_", 4L);
        storedDocument.setField(SolrBookmarkStore.PAGES_FIELD, "wiki:Space.Page");
        SolrDocumentList documents = new SolrDocumentList();
        documents.add(storedDocument);
        QueryResponse response = mock(QueryResponse.class);
        when(response.getResults()).thenReturn(documents);
        when(client.query(any(SolrQuery.class))).thenReturn(response);

        long version = store.save("wiki", "XWiki.User", BOOKMARK_JSON, 3L);

        assertEquals(4L, version);
        org.mockito.ArgumentCaptor<SolrInputDocument> documentCaptor =
            org.mockito.ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(client).add(documentCaptor.capture());
        SolrInputDocument saved = documentCaptor.getValue();
        assertEquals("wiki", saved.getFieldValue(SolrBookmarkStore.WIKI_FIELD));
        assertEquals("XWiki.User", saved.getFieldValue(SolrBookmarkStore.USER_FIELD));
        assertEquals("wiki:Space.Page", saved.getFieldValue(SolrBookmarkStore.PAGES_FIELD));
        assertEquals(3L, saved.getFieldValue("_version_"));
    }

    @Test
    void rejectsInvalidJsonBeforeCallingSolr() throws Exception
    {
        assertThrows(BookmarkStoreException.class, () -> store.save("wiki", "XWiki.User", "{}", 0L));

        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void reportsAConflictIfAnotherWriteReplacesTheDataBeforeVerification() throws Exception
    {
        SolrDocument storedDocument = new SolrDocument();
        storedDocument.setField(SolrBookmarkStore.DATA_FIELD, "{\"folders\":[],\"bookmarks\":[]}");
        storedDocument.setField("_version_", 5L);
        SolrDocumentList documents = new SolrDocumentList();
        documents.add(storedDocument);
        QueryResponse response = mock(QueryResponse.class);
        when(response.getResults()).thenReturn(documents);
        when(client.query(any(SolrQuery.class))).thenReturn(response);

        assertThrows(BookmarkStoreConflictException.class, () -> store.save("wiki", "XWiki.User",
            "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:Space.Page\"}]}", 3L));
    }

    @Test
    void mapsTheNativeSolrVersionConflict() throws Exception
    {
        when(client.add(any(SolrInputDocument.class))).thenThrow(
            new org.apache.solr.common.SolrException(org.apache.solr.common.SolrException.ErrorCode.CONFLICT,
                "version conflict"));

        assertThrows(BookmarkStoreConflictException.class,
            () -> store.save("wiki", "XWiki.User", BOOKMARK_JSON, 3L));
    }

    @Test
    void replacesPageReferencesAndUpdatesTheSolrIndex() throws Exception
    {
        QueryResponse usersResponse = responseWith(document(SolrBookmarkStore.USER_FIELD, "XWiki.User"));
        when(usersResponse.getNextCursorMark()).thenReturn("*");
        QueryResponse currentResponse = responseWith(bookmarkDocument(
            "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:Old.Page\"}]}", 3L, "wiki:Old.Page"));
        QueryResponse savedResponse = responseWith(bookmarkDocument(
            "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:New.Page\"}]}", 4L, "wiki:New.Page"));
        when(client.query(any(SolrQuery.class))).thenReturn(usersResponse, currentResponse, savedResponse);

        assertEquals(1, store.replacePage("wiki", "wiki:Old.Page", "wiki:New.Page"));

        org.mockito.ArgumentCaptor<SolrInputDocument> documentCaptor =
            org.mockito.ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(client).add(documentCaptor.capture());
        assertEquals("{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:New.Page\"}]}",
            documentCaptor.getValue().getFieldValue(SolrBookmarkStore.DATA_FIELD));
        assertEquals("wiki:New.Page", documentCaptor.getValue().getFieldValue(SolrBookmarkStore.PAGES_FIELD));
    }

    private static QueryResponse responseWith(SolrDocument... sourceDocuments)
    {
        SolrDocumentList documents = new SolrDocumentList();
        documents.addAll(java.util.List.of(sourceDocuments));
        QueryResponse response = mock(QueryResponse.class);
        when(response.getResults()).thenReturn(documents);
        return response;
    }

    private static SolrDocument document(String field, Object value)
    {
        SolrDocument document = new SolrDocument();
        document.setField(field, value);
        return document;
    }

    private static SolrDocument bookmarkDocument(String json, long version, String page)
    {
        SolrDocument document = document(SolrBookmarkStore.DATA_FIELD, json);
        document.setField("_version_", version);
        document.setField(SolrBookmarkStore.PAGES_FIELD, page);
        return document;
    }
}

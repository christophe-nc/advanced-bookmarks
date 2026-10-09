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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.bridge.event.DocumentDeletedEvent;
import org.xwiki.bridge.event.WikiDeletedEvent;
import org.xwiki.contrib.advancedbookmarks.BookmarkStore;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.observation.event.Event;
import org.xwiki.refactoring.event.DocumentRenamedEvent;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests that stored bookmarks follow renamed pages, deleted users and deleted wikis.
 *
 * @version $Id$
 */
@ComponentTest
class BookmarkEventListenerTest
{
    private static final DocumentReference OLD_PAGE = new DocumentReference("wiki", "Old", "Page");

    private static final DocumentReference NEW_PAGE = new DocumentReference("wiki", "New", "Page");

    private static final DocumentReference USER = new DocumentReference("xwiki", "XWiki", "alice");

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.INFO);

    @InjectMockComponents
    private BookmarkEventListener listener;

    @MockComponent
    private BookmarkStore store;

    @BeforeComponent
    void registerReferences(MockitoComponentManager componentManager) throws Exception
    {
        ReferenceStubs.register(componentManager);
    }

    @Test
    void listensToRenamesAndDeletions()
    {
        assertEquals(BookmarkEventListener.NAME, this.listener.getName());
        List<Event> events = this.listener.getEvents();
        assertEquals(3, events.size());
    }

    @Test
    void replacesARenamedPage() throws Exception
    {
        when(this.store.replacePage("wiki", "wiki:Old.Page", "wiki:New.Page")).thenReturn(2);

        this.listener.processLocalEvent(new DocumentRenamedEvent(OLD_PAGE, NEW_PAGE), null, null);

        verify(this.store).replacePage("wiki", "wiki:Old.Page", "wiki:New.Page");
    }

    @Test
    void ignoresAPageMovedToAnotherWiki()
    {
        DocumentReference otherWikiPage = new DocumentReference("other", "New", "Page");

        this.listener.processLocalEvent(new DocumentRenamedEvent(OLD_PAGE, otherWikiPage), null, null);
        this.listener.processLocalEvent(new DocumentRenamedEvent(), null, null);

        verifyNoInteractions(this.store);
    }

    @Test
    void neverPropagatesAStorageFailure() throws Exception
    {
        when(this.store.replacePage(anyString(), anyString(), anyString()))
            .thenThrow(new BookmarkStoreConflictException(null));

        this.listener.processLocalEvent(new DocumentRenamedEvent(OLD_PAGE, NEW_PAGE), null, null);

        verify(this.store).replacePage(anyString(), anyString(), anyString());
        assertTrue(this.logCapture.getMessage(0).startsWith("Failed to update the bookmarks after the event"));
    }

    @Test
    void erasesTheBookmarksOfADeletedUser() throws Exception
    {
        this.listener.processLocalEvent(new DocumentDeletedEvent(USER), deletedDocument(USER, true), null);

        verify(this.store).deleteUser("xwiki:XWiki.alice");
        assertEquals("Deleted the bookmarks of the deleted user [xwiki:XWiki.alice].", this.logCapture.getMessage(0));
    }

    @Test
    void keepsTheBookmarksOfADeletedPage()
    {
        this.listener.processLocalEvent(new DocumentDeletedEvent(OLD_PAGE), deletedDocument(OLD_PAGE, false), null);
        this.listener.processLocalEvent(new DocumentDeletedEvent(OLD_PAGE), "not a document", null);

        verifyNoInteractions(this.store);
    }

    @Test
    void erasesTheBookmarksOfADeletedWiki() throws Exception
    {
        this.listener.processLocalEvent(new WikiDeletedEvent("subwiki"), "subwiki", null);

        verify(this.store).deleteWiki("subwiki");
        assertEquals("Deleted the bookmarks of the deleted wiki [subwiki].", this.logCapture.getMessage(0));
    }

    @Test
    void neverPropagatesADeletionFailure() throws Exception
    {
        doThrow(new BookmarkStoreException("down", null)).when(this.store).deleteWiki(anyString());

        this.listener.processLocalEvent(new WikiDeletedEvent("subwiki"), "subwiki", null);

        verify(this.store).deleteWiki("subwiki");
        assertTrue(this.logCapture.getMessage(0).startsWith("Failed to update the bookmarks after the event"));
    }

    private static XWikiDocument deletedDocument(DocumentReference reference, boolean user)
    {
        XWikiDocument original = mock(XWikiDocument.class);
        when(original.getDocumentReference()).thenReturn(reference);
        when(original.getXObject(any(EntityReference.class))).thenReturn(user ? new BaseObject() : null);
        XWikiDocument deleted = mock(XWikiDocument.class);
        when(deleted.getOriginalDocument()).thenReturn(original);
        return deleted;
    }
}

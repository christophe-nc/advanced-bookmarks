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

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Tests the resolution of the references received from scripts and requests.
 *
 * @version $Id$
 */
@ComponentTest
class BookmarkReferencesTest
{
    private static final DocumentReference ALICE = ReferenceStubs.page("wiki:XWiki.alice");

    private static final String TOO_LONG = "a".repeat(BookmarkJsonCodec.MAX_PAGE_REFERENCE_LENGTH + 1);

    @InjectMockComponents
    private BookmarkReferences references;

    @MockComponent
    private BookmarkRights rights;

    @BeforeComponent
    void registerReferences(MockitoComponentManager componentManager) throws Exception
    {
        ReferenceStubs.register(componentManager);
    }

    @Test
    void resolvesAMissingUserToTheCurrentUser() throws Exception
    {
        when(this.rights.getCurrentUser()).thenReturn(ALICE);

        assertEquals(ALICE, this.references.resolveUser(null));
        assertEquals(ALICE, this.references.resolveUser(" "));
    }

    @Test
    void resolvesAGuestToNoUser() throws Exception
    {
        assertNull(this.references.resolveUser(""));
    }

    @Test
    void resolvesAUserNameInTheUserSpace() throws Exception
    {
        assertEquals(ReferenceStubs.page("wiki:XWiki.bob"), this.references.resolveUser("bob"));
        assertEquals(ReferenceStubs.page("other:XWiki.bob"), this.references.resolveUser("other:XWiki.bob"));
    }

    @Test
    void resolvesAPage() throws Exception
    {
        assertEquals(ReferenceStubs.page("wiki:Space.Page"), this.references.resolvePage("Space.Page"));
    }

    @Test
    void rejectsEmptyPagesAndTooLongReferences()
    {
        assertThrows(BookmarkInvalidDataException.class, () -> this.references.resolvePage(""));
        assertThrows(BookmarkInvalidDataException.class, () -> this.references.resolvePage(TOO_LONG));
        assertThrows(BookmarkInvalidDataException.class, () -> this.references.resolveUser(TOO_LONG));
    }
}

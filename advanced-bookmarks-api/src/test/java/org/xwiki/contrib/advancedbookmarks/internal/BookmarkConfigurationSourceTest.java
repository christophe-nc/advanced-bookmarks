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
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Tests the location of the configuration read by {@link BookmarkConfigurationSource}.
 *
 * @version $Id$
 */
@ComponentTest
class BookmarkConfigurationSourceTest
{
    private static final List<String> CODE_SPACE = List.of("AdvancedBookmarks", "Code");

    @InjectMockComponents
    private BookmarkConfigurationSource source;

    @MockComponent
    private WikiDescriptorManager wikiManager;

    @Test
    void readsTheConfigurationPageOfTheCurrentWiki()
    {
        when(this.wikiManager.getCurrentWikiId()).thenReturn("subwiki");

        assertEquals(new DocumentReference("subwiki", CODE_SPACE, "Configuration"),
            this.source.getDocumentReference());
        assertEquals(new LocalDocumentReference(CODE_SPACE, "ConfigurationClass"), this.source.getClassReference());
        assertEquals("configuration.document.advancedbookmarks", this.source.getCacheId());
    }
}

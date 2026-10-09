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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the schema of the Advanced Bookmarks Solr core.
 *
 * @version $Id$
 */
class BookmarkSolrCoreInitializerTest
{
    /**
     * Records the declared fields instead of sending them to a Solr core.
     */
    private static final class RecordingInitializer extends BookmarkSolrCoreInitializer
    {
        private final List<String> fields = new ArrayList<>();

        @Override
        protected void addStringField(String name, boolean multiValued, boolean dynamic)
        {
            this.fields.add(name + (multiValued ? "[]" : ""));
        }

        @Override
        protected void addField(String name, String type, boolean dynamic, Object... attributes)
        {
            this.fields.add(name + ':' + type);
        }
    }

    @Test
    void declaresTheBookmarkFields() throws Exception
    {
        RecordingInitializer initializer = new RecordingInitializer();

        initializer.createSchema();
        initializer.migrateSchema(0);

        assertEquals(List.of("wiki", "user", "data:string", "pages[]"), initializer.fields);
        assertEquals(1000000L, initializer.getVersion());
    }
}

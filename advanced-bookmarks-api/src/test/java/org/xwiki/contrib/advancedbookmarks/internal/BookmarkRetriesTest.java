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
import org.xwiki.contrib.advancedbookmarks.BookmarkException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the retries of bookmark updates after a version conflict.
 *
 * @version $Id$
 */
class BookmarkRetriesTest
{
    /**
     * An update that meets a version conflict a given number of times before succeeding.
     */
    private static final class ConflictingUpdate implements BookmarkRetries.Update<String, BookmarkException>
    {
        private final int conflicts;

        private int attempts;

        ConflictingUpdate(int conflicts)
        {
            this.conflicts = conflicts;
        }

        @Override
        public String run() throws BookmarkStoreConflictException
        {
            this.attempts++;
            if (this.attempts <= this.conflicts) {
                throw new BookmarkStoreConflictException(null);
            }
            return "updated";
        }
    }

    @Test
    void runsAnUpdateOnceWithoutConflict() throws Exception
    {
        ConflictingUpdate update = new ConflictingUpdate(0);

        assertEquals("updated", BookmarkRetries.onConflict(update));
        assertEquals(1, update.attempts);
    }

    @Test
    void retriesAnUpdateAfterConflicts() throws Exception
    {
        ConflictingUpdate update = new ConflictingUpdate(BookmarkRetries.MAX_ATTEMPTS - 1);

        assertEquals("updated", BookmarkRetries.onConflict(update));
        assertEquals(BookmarkRetries.MAX_ATTEMPTS, update.attempts);
    }

    @Test
    void reportsTheConflictOfTheLastAttempt()
    {
        ConflictingUpdate update = new ConflictingUpdate(BookmarkRetries.MAX_ATTEMPTS);

        assertThrows(BookmarkStoreConflictException.class, () -> BookmarkRetries.onConflict(update));
        assertEquals(BookmarkRetries.MAX_ATTEMPTS, update.attempts);
    }

    @Test
    void doesNotRetryOtherFailures()
    {
        BookmarkStoreException failure = new BookmarkStoreException("down", null);
        int[] attempts = {0};

        assertSame(failure, assertThrows(BookmarkStoreException.class, () -> BookmarkRetries.onConflict(() -> {
            attempts[0]++;
            throw failure;
        })));
        assertEquals(1, attempts[0]);
    }
}

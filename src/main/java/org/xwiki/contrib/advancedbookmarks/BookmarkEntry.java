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
package org.xwiki.contrib.advancedbookmarks;

import java.util.Objects;

/**
 * A bookmark that references a page in the wiki.
 *
 * @since 1.0
 */
public class BookmarkEntry {
    private final String page;
    private final String label;
    private final String folderId;
    private final int order;
    private final boolean uncategorized;
    private boolean deleted;

    /**
     * Create a bookmark entry.
     *
     * @param page the serialized page reference
     * @param label the optional display label
     * @param folderId the containing folder identifier
     * @param order the position within the folder
     * @param uncategorized whether the entry is in the default folder
     */
    public BookmarkEntry(String page, String label, String folderId, int order, boolean uncategorized) {
        this.page = Objects.requireNonNull(page, "page must not be null");
        this.label = Objects.requireNonNull(label, "label must not be null");
        this.folderId = folderId;
        this.order = order;
        this.uncategorized = uncategorized;
    }

    /**
     * @return the serialized page reference
     */
    public String getPage() {
        return page;
    }

    /**
     * @return the optional display label
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return the containing folder identifier
     */
    public String getFolderId() {
        return folderId;
    }

    /**
     * @return the position within the folder
     */
    public int getOrder() {
        return order;
    }

    /**
     * @return whether the entry is in the default folder
     */
    public boolean isUncategorized() {
        return uncategorized;
    }

    /**
     * @return whether the bookmarked page has been deleted
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Mark whether the bookmarked page has been deleted.
     *
     * @param deleted the deleted state
     * @return this bookmark
     */
    public BookmarkEntry withDeletedState(boolean deleted) {
        this.deleted = deleted;
        return this;
    }
}

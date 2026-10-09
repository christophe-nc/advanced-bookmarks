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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Provides bookmark ordering and panel-size normalization.
 *
 * @since 1.0
 */
public class BookmarkManager {
    /** Maximum number of bookmarks displayed in the panel. */
    public static final int DEFAULT_PANEL_SIZE = 10;

    /**
     * Normalize a configured panel size to the supported range.
     *
     * @param requestedPanelSize the requested panel size
     * @return the effective panel size
     */
    public int normalizePanelSize(Integer requestedPanelSize) {
        if (requestedPanelSize == null || requestedPanelSize <= 0) {
            return DEFAULT_PANEL_SIZE;
        }
        return Math.min(requestedPanelSize, DEFAULT_PANEL_SIZE);
    }

    /**
     * Check whether a panel size is supported.
     *
     * @param panelSize the requested panel size
     * @return {@code true} if the size is between one and the default maximum
     */
    public boolean isPanelAllowed(int panelSize) {
        return panelSize > 0 && panelSize <= DEFAULT_PANEL_SIZE;
    }

    /**
     * Sort folders with uncategorized first, then by explicit order and name.
     *
     * @param folders folders to sort
     * @return a sorted copy, or an empty list
     */
    public List<BookmarkFolder> sortFolders(List<BookmarkFolder> folders) {
        if (folders == null || folders.isEmpty()) {
            return List.of();
        }

        List<BookmarkFolder> sorted = new ArrayList<>(folders);
        sorted.sort(Comparator
            .comparingInt((BookmarkFolder folder) -> folder.isUncategorized() ? 0 : 1)
            .thenComparingInt(BookmarkFolder::getOrder)
            .thenComparing(BookmarkFolder::getName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /**
     * Sort a folder hierarchy recursively.
     *
     * @param folder the root folder to sort
     */
    public void sortHierarchy(BookmarkFolder folder) {
        if (folder == null) {
            return;
        }

        folder.getChildren().sort(Comparator
            .comparingInt((BookmarkFolder child) -> child.isUncategorized() ? 0 : 1)
            .thenComparingInt(BookmarkFolder::getOrder)
            .thenComparing(BookmarkFolder::getName, String.CASE_INSENSITIVE_ORDER));

        for (BookmarkFolder child : folder.getChildren()) {
            sortHierarchy(child);
        }
    }

    /**
     * Sort bookmarks with uncategorized entries first, then by explicit order and label.
     *
     * @param bookmarks bookmarks to sort
     * @return a sorted copy, or an empty list
     */
    public List<BookmarkEntry> sortBookmarks(List<BookmarkEntry> bookmarks) {
        if (bookmarks == null || bookmarks.isEmpty()) {
            return List.of();
        }

        List<BookmarkEntry> sorted = new ArrayList<>(bookmarks);
        sorted.sort(Comparator
            .comparingInt((BookmarkEntry bookmark) -> bookmark.isUncategorized() ? 0 : 1)
            .thenComparingInt(BookmarkEntry::getOrder)
            .thenComparing(BookmarkEntry::getLabel, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /**
     * Return a bookmark with a new label.
     *
     * @param bookmark the bookmark to update
     * @param newLabel its new label
     * @return the updated bookmark, or {@code null} if the input is {@code null}
     */
    public BookmarkEntry renameBookmark(BookmarkEntry bookmark, String newLabel) {
        if (bookmark == null) {
            return null;
        }
        return new BookmarkEntry(bookmark.getPage(), newLabel, bookmark.getFolderId(), bookmark.getOrder(),
            bookmark.isUncategorized());
    }

    /**
     * Mark a bookmark as pointing to a deleted page.
     *
     * @param bookmark the bookmark to update
     * @return the updated bookmark, or {@code null} if the input is {@code null}
     */
    public BookmarkEntry removeDeletedEntry(BookmarkEntry bookmark) {
        if (bookmark == null) {
            return null;
        }
        return bookmark.withDeletedState(true);
    }
}

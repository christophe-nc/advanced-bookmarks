package org.xwiki.contrib.advancedbookmarks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BookmarkManager {
    public static final int DEFAULT_PANEL_SIZE = 10;

    public int normalizePanelSize(Integer requestedPanelSize) {
        if (requestedPanelSize == null || requestedPanelSize <= 0) {
            return DEFAULT_PANEL_SIZE;
        }
        return Math.min(requestedPanelSize, DEFAULT_PANEL_SIZE);
    }

    public boolean isPanelAllowed(int panelSize) {
        return panelSize > 0 && panelSize <= DEFAULT_PANEL_SIZE;
    }

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

    public BookmarkEntry renameBookmark(BookmarkEntry bookmark, String newLabel) {
        if (bookmark == null) {
            return null;
        }
        return new BookmarkEntry(bookmark.getPage(), newLabel, bookmark.getFolderId(), bookmark.getOrder(),
            bookmark.isUncategorized());
    }

    public BookmarkEntry removeDeletedEntry(BookmarkEntry bookmark) {
        if (bookmark == null) {
            return null;
        }
        return bookmark.withDeletedState(true);
    }
}

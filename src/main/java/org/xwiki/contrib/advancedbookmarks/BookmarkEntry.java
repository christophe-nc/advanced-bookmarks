package org.xwiki.contrib.advancedbookmarks;

import java.util.Objects;

public class BookmarkEntry {
    private final String page;
    private final String label;
    private final String folderId;
    private final int order;
    private final boolean uncategorized;
    private boolean deleted;

    public BookmarkEntry(String page, String label, String folderId, int order, boolean uncategorized) {
        this.page = Objects.requireNonNull(page, "page must not be null");
        this.label = Objects.requireNonNull(label, "label must not be null");
        this.folderId = folderId;
        this.order = order;
        this.uncategorized = uncategorized;
    }

    public String getPage() {
        return page;
    }

    public String getLabel() {
        return label;
    }

    public String getFolderId() {
        return folderId;
    }

    public int getOrder() {
        return order;
    }

    public boolean isUncategorized() {
        return uncategorized;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public BookmarkEntry withDeletedState(boolean deleted) {
        this.deleted = deleted;
        return this;
    }
}

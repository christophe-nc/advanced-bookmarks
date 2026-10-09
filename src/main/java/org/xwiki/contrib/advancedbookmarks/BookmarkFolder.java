package org.xwiki.contrib.advancedbookmarks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BookmarkFolder {
    private final String id;
    private final String name;
    private final String parentId;
    private final int order;
    private final boolean uncategorized;
    private final List<BookmarkFolder> children = new ArrayList<>();

    public BookmarkFolder(String id, String name, String parentId, int order, boolean uncategorized) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.parentId = parentId;
        this.order = order;
        this.uncategorized = uncategorized;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getParentId() {
        return parentId;
    }

    public int getOrder() {
        return order;
    }

    public boolean isUncategorized() {
        return uncategorized;
    }

    public List<BookmarkFolder> getChildren() {
        return children;
    }

    public void addChild(BookmarkFolder child) {
        if (child == null) {
            return;
        }
        children.add(child);
    }
}

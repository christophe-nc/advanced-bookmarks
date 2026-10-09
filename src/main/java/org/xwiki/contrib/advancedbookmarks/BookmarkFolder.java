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
import java.util.List;
import java.util.Objects;

/**
 * A bookmark folder that can contain child folders.
 *
 * @since 1.0
 */
public class BookmarkFolder {
    private final String id;
    private final String name;
    private final String parentId;
    private final int order;
    private final boolean uncategorized;
    private final List<BookmarkFolder> children = new ArrayList<>();

    /**
     * Create a bookmark folder.
     *
     * @param id the unique folder identifier
     * @param name the display name
     * @param parentId the parent folder identifier
     * @param order the position among siblings
     * @param uncategorized whether this is the default folder
     */
    public BookmarkFolder(String id, String name, String parentId, int order, boolean uncategorized) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.parentId = parentId;
        this.order = order;
        this.uncategorized = uncategorized;
    }

    /**
     * @return the unique folder identifier
     */
    public String getId() {
        return id;
    }

    /**
     * @return the display name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the parent folder identifier
     */
    public String getParentId() {
        return parentId;
    }

    /**
     * @return the position among siblings
     */
    public int getOrder() {
        return order;
    }

    /**
     * @return whether this is the default folder
     */
    public boolean isUncategorized() {
        return uncategorized;
    }

    /**
     * @return the child folders
     */
    public List<BookmarkFolder> getChildren() {
        return children;
    }

    /**
     * Add a child folder.
     *
     * @param child the folder to add
     */
    public void addChild(BookmarkFolder child) {
        if (child == null) {
            return;
        }
        children.add(child);
    }
}

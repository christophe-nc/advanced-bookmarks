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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkException;
import org.xwiki.contrib.advancedbookmarks.BookmarkFolder;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkManager;
import org.xwiki.contrib.advancedbookmarks.BookmarkRecord;
import org.xwiki.contrib.advancedbookmarks.BookmarkStore;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.model.reference.DocumentReference;

/**
 * Default implementation of {@link BookmarkManager}, storing the bookmarks of each user of the current wiki through
 * the {@link BookmarkStore}.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultBookmarkManager implements BookmarkManager
{
    private static final int MAX_TOGGLE_ATTEMPTS = 3;

    @Inject
    private BookmarkStore store;

    @Inject
    private BookmarkJsonCodec jsonCodec;

    @Inject
    private BookmarkNormalizer normalizer;

    @Inject
    private BookmarkRights rights;

    @Inject
    private BookmarkPages pages;

    @Inject
    private Logger logger;

    /**
     * A step of the depth-first traversal of the folder tree: either visit a folder, or emit its own bookmarks.
     */
    private record Step(String folderId, boolean emitBookmarks)
    {
    }

    @Override
    public boolean canManage(DocumentReference user)
    {
        return this.rights.canManage(user);
    }

    @Override
    public BookmarkData getBookmarks(DocumentReference user) throws BookmarkException
    {
        checkAccess(user);
        return read(user);
    }

    @Override
    public boolean isBookmarked(DocumentReference user, DocumentReference page) throws BookmarkException
    {
        checkAccess(user);
        String pageReference = this.normalizer.serialize(page);
        return read(user).getBookmarks().stream().anyMatch(bookmark -> pageReference.equals(bookmark.getPage()));
    }

    @Override
    public boolean toggle(DocumentReference user, DocumentReference page) throws BookmarkException
    {
        checkAccess(user);
        String wiki = this.rights.getCurrentWiki();
        if (!wiki.equals(page.getWikiReference().getName())) {
            throw new BookmarkInvalidDataException(null);
        }
        String pageReference = this.normalizer.serialize(page);
        for (int attempt = 1;; attempt++) {
            BookmarkData data = read(user);
            boolean removed = data.getBookmarks().removeIf(bookmark -> pageReference.equals(bookmark.getPage()));
            if (!removed) {
                data.getBookmarks().add(createBookmark(page, pageReference, data));
            }
            try {
                write(user, data);
                return !removed;
            } catch (BookmarkStoreConflictException e) {
                // Usually another browser tab: re-read and apply the toggle on the latest data.
                if (attempt >= MAX_TOGGLE_ATTEMPTS) {
                    throw e;
                }
                this.logger.debug("Retrying a bookmark toggle after a version conflict.");
            }
        }
    }

    @Override
    public long save(DocumentReference user, BookmarkData data) throws BookmarkException
    {
        checkAccess(user);
        this.normalizer.normalize(data, this.rights.getCurrentWiki());
        // Storing the bookmarks in display order keeps the stored data readable and the order stable.
        data.setBookmarks(getDisplayOrder(data));
        return write(user, data);
    }

    @Override
    public List<BookmarkEntry> getDisplayOrder(BookmarkData data)
    {
        Map<String, List<BookmarkEntry>> bookmarksByFolder = data.getBookmarks().stream()
            .sorted(Comparator.comparingInt(BookmarkEntry::getOrder))
            .collect(Collectors.groupingBy(BookmarkEntry::getFolder));
        Map<String, List<BookmarkFolder>> childrenByParent = data.getFolders().stream()
            .sorted(Comparator.comparingInt(BookmarkFolder::getOrder))
            .collect(Collectors.groupingBy(BookmarkFolder::getParent));

        List<BookmarkEntry> result = new ArrayList<>(bookmarksByFolder.getOrDefault(BookmarkFolder.ROOT, List.of()));
        // An explicit stack rather than recursion, since the folder tree may be thousands of levels deep.
        Deque<Step> stack = new ArrayDeque<>();
        pushChildren(stack, childrenByParent.getOrDefault(BookmarkFolder.ROOT, List.of()));
        while (!stack.isEmpty()) {
            Step step = stack.pop();
            if (step.emitBookmarks()) {
                result.addAll(bookmarksByFolder.getOrDefault(step.folderId(), List.of()));
            } else {
                stack.push(new Step(step.folderId(), true));
                pushChildren(stack, childrenByParent.getOrDefault(step.folderId(), List.of()));
            }
        }
        return result;
    }

    @Override
    public List<BookmarkEntry> getPanel(BookmarkData data)
    {
        List<BookmarkEntry> ordered = getDisplayOrder(data);
        List<BookmarkEntry> selected =
            ordered.stream().filter(BookmarkEntry::isPanel).limit(MAX_PANEL_SIZE).collect(Collectors.toList());
        return selected.isEmpty() ? ordered.stream().limit(MAX_PANEL_SIZE).collect(Collectors.toList()) : selected;
    }

    private static void pushChildren(Deque<Step> stack, List<BookmarkFolder> children)
    {
        // Pushed in reverse so that the first child is visited first.
        for (int i = children.size() - 1; i >= 0; i--) {
            stack.push(new Step(children.get(i).getId(), false));
        }
    }

    private void checkAccess(DocumentReference user) throws BookmarkAccessDeniedException
    {
        if (!this.rights.canManage(user)) {
            throw new BookmarkAccessDeniedException();
        }
    }

    private BookmarkEntry createBookmark(DocumentReference page, String pageReference, BookmarkData data)
        throws BookmarkException
    {
        if (!this.rights.canView(page)) {
            throw new BookmarkAccessDeniedException();
        }
        if (data.getBookmarks().size() >= BookmarkJsonCodec.MAX_ITEMS) {
            throw new BookmarkInvalidDataException(null);
        }
        BookmarkEntry bookmark = new BookmarkEntry();
        bookmark.setPage(pageReference);
        bookmark.setLabel(this.pages.exists(page)
            ? BookmarkNormalizer.bound(this.pages.getPlainTitle(page), BookmarkNormalizer.MAX_LABEL_LENGTH) : "");
        bookmark.setFolder(BookmarkFolder.ROOT);
        bookmark.setOrder((int) data.getBookmarks().stream()
            .filter(existing -> BookmarkFolder.ROOT.equals(existing.getFolder())).count());
        return bookmark;
    }

    private BookmarkData read(DocumentReference user) throws BookmarkStoreException
    {
        String wiki = this.rights.getCurrentWiki();
        Optional<BookmarkRecord> stored = this.store.get(wiki, this.normalizer.serialize(user));
        if (stored.isEmpty()) {
            return new BookmarkData();
        }
        BookmarkData data;
        try {
            data = this.jsonCodec.read(stored.get().json());
            this.normalizer.normalize(data, wiki);
        } catch (BookmarkInvalidDataException e) {
            // The data was validated when written: failing now means the stored document is corrupted.
            throw new BookmarkStoreException("The stored bookmark data is corrupted.", e);
        }
        data.setVersion(stored.get().version());
        return data;
    }

    private long write(DocumentReference user, BookmarkData data) throws BookmarkStoreException
    {
        return this.store.save(this.rights.getCurrentWiki(), this.normalizer.serialize(user),
            this.jsonCodec.write(data), data.getVersion());
    }
}

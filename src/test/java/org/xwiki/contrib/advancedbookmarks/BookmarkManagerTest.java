package org.xwiki.contrib.advancedbookmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BookmarkManagerTest {
    @Test
    void defaultPanelSizeIsTen() {
        BookmarkManager manager = new BookmarkManager();

        assertEquals(10, manager.normalizePanelSize(null));
        assertEquals(10, manager.normalizePanelSize(100));
        assertTrue(manager.isPanelAllowed(10));
    }

    @Test
    void bookmarksMacroRendersDefaultHomePageAndLink() {
        BookmarksMacro macro = new BookmarksMacro();

        String rendered = macro.render();

        assertTrue(rendered.contains("Advanced Bookmarks"));
        assertTrue(rendered.contains("AdvancedBookmarks.WebHome"));
        assertTrue(rendered.contains("View all my bookmarks"));
    }

    @Test
    void bookmarksMacroSupportsPanelConfiguration() {
        BookmarksMacro macro = new BookmarksMacro();

        String rendered = macro.render(8, true, true);

        assertTrue(rendered.contains("data-panel-size=\"8\""));
        assertTrue(rendered.contains("data-read-only=\"true\""));
        assertTrue(rendered.contains("data-compact=\"true\""));
    }

    @Test
    void foldersAreSortedWithUncategorizedFirstAndByOrder() {
        BookmarkManager manager = new BookmarkManager();
        BookmarkFolder uncategorized = new BookmarkFolder("uncategorized", "Non classés", null, 0, true);
        BookmarkFolder work = new BookmarkFolder("work", "Work", null, 2, false);
        BookmarkFolder home = new BookmarkFolder("home", "Home", null, 1, false);

        List<BookmarkFolder> sorted = manager.sortFolders(List.of(work, uncategorized, home));

        assertEquals(List.of("uncategorized", "home", "work"), sorted.stream().map(BookmarkFolder::getId).toList());
    }

    @Test
    void childrenAreNestedInHierarchyAndSortedRecursively() {
        BookmarkManager manager = new BookmarkManager();
        BookmarkFolder root = new BookmarkFolder("root", "Root", null, 0, false);
        BookmarkFolder childB = new BookmarkFolder("childB", "Child B", "root", 2, false);
        BookmarkFolder childA = new BookmarkFolder("childA", "Child A", "root", 1, false);
        BookmarkFolder nested = new BookmarkFolder("nested", "Nested", "childA", 1, false);

        root.addChild(childB);
        root.addChild(childA);
        childA.addChild(nested);

        manager.sortHierarchy(root);

        assertEquals(List.of("childA", "childB"), root.getChildren().stream().map(BookmarkFolder::getId).toList());
        assertEquals(List.of("nested"), childA.getChildren().stream().map(BookmarkFolder::getId).toList());
    }

    @Test
    void bookmarkEntriesAreSortedByOrderAndDefaultToUncategorized() {
        BookmarkManager manager = new BookmarkManager();
        BookmarkEntry second = new BookmarkEntry("page-2", "Page 2", null, 2, false);
        BookmarkEntry first = new BookmarkEntry("page-1", "Page 1", null, 1, false);
        BookmarkEntry defaultOne = new BookmarkEntry("page-0", "Page 0", null, 0, true);

        List<BookmarkEntry> sorted = manager.sortBookmarks(List.of(second, first, defaultOne));

        assertEquals(List.of("page-0", "page-1", "page-2"), sorted.stream().map(BookmarkEntry::getPage).toList());
    }

    @Test
    void bookmarkUpdateKeepsReferencedPageAndRemovesDeletedEntry() {
        BookmarkManager manager = new BookmarkManager();
        BookmarkEntry entry = new BookmarkEntry("page-1", "Page 1", null, 1, false);
        BookmarkEntry renamed = manager.renameBookmark(entry, "Page 1 renamed");

        assertEquals("Page 1 renamed", renamed.getLabel());
        assertEquals("page-1", renamed.getPage());

        BookmarkEntry removed = manager.removeDeletedEntry(entry);
        assertTrue(removed.isDeleted());
    }
}

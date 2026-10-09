package org.xwiki.contrib.advancedbookmarks;

public class BookmarksMacro {
    public static final String DEFAULT_HOME_PAGE = "AdvancedBookmarks.WebHome";
    public static final String DEFAULT_TITLE = "Advanced Bookmarks";
    public static final String DEFAULT_VIEW_ALL_LABEL = "View all my bookmarks";

    private final BookmarkManager bookmarkManager;

    public BookmarksMacro() {
        this(new BookmarkManager());
    }

    public BookmarksMacro(BookmarkManager bookmarkManager) {
        this.bookmarkManager = bookmarkManager;
    }

    public int getDefaultPanelSize() {
        return bookmarkManager.normalizePanelSize(null);
    }

    public String getDefaultHomePage() {
        return DEFAULT_HOME_PAGE;
    }

    public String getMacroName() {
        return "bookmarks";
    }

    public String render() {
        StringBuilder html = new StringBuilder();
        html.append("<section class=\"advanced-bookmarks\" aria-label=\"Advanced Bookmarks\">\n");
        html.append("  <h2>").append(DEFAULT_TITLE).append("</h2>\n");
        html.append("  <p><a href=\"/xwiki/bin/view/").append(DEFAULT_HOME_PAGE).append("\">")
            .append(DEFAULT_VIEW_ALL_LABEL)
            .append("</a></p>\n");
        html.append("  <ul>\n");
        html.append("    <li>Default folder</li>\n");
        html.append("    <li>Current page</li>\n");
        html.append("    <li>Quick access</li>\n");
        html.append("  </ul>\n");
        html.append("</section>");
        return html.toString();
    }
}

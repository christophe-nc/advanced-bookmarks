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

/**
 * Renders the placeholder markup of the bookmarks macro.
 *
 * @version $Id$
 * @since 1.0
 */
public class BookmarksMacro
{
    /** Default home page for bookmark management. */
    public static final String DEFAULT_HOME_PAGE = "AdvancedBookmarks.WebHome";

    /** Default macro title. */
    public static final String DEFAULT_TITLE = "Advanced Bookmarks";

    /** Default label for the link to the full bookmark list. */
    public static final String DEFAULT_VIEW_ALL_LABEL = "View all my bookmarks";

    private static final String ATTRIBUTE_END = "\"";

    private static final String TAG_END = "\">";

    /**
     * @return the default panel size
     */
    public int getDefaultPanelSize()
    {
        return BookmarkManager.MAX_PANEL_SIZE;
    }

    /**
     * @return the default management page reference
     */
    public String getDefaultHomePage()
    {
        return DEFAULT_HOME_PAGE;
    }

    /**
     * @return the macro name
     */
    public String getMacroName()
    {
        return "bookmarks";
    }

    /**
     * Render the default macro configuration.
     *
     * @return rendered HTML
     */
    public String render()
    {
        return render(getDefaultPanelSize(), false, false);
    }

    /**
     * Render the macro with the requested panel configuration.
     *
     * @param panelSize the requested panel size, bounded to {@link BookmarkManager#MAX_PANEL_SIZE}
     * @param readOnly whether bookmark changes are disabled
     * @param compact whether compact layout is requested
     * @return rendered HTML
     */
    public String render(int panelSize, boolean readOnly, boolean compact)
    {
        int effectivePanelSize = panelSize <= 0 ? BookmarkManager.MAX_PANEL_SIZE
            : Math.min(panelSize, BookmarkManager.MAX_PANEL_SIZE);

        StringBuilder html = new StringBuilder();
        html.append("<section class=\"advanced-bookmarks\" aria-label=\"Advanced Bookmarks\"")
            .append(" data-panel-size=\"").append(effectivePanelSize).append(ATTRIBUTE_END)
            .append(" data-read-only=\"").append(readOnly).append(ATTRIBUTE_END)
            .append(" data-compact=\"").append(compact).append(TAG_END)
            .append("\n");
        html.append("  <h2>").append(DEFAULT_TITLE).append("</h2>\n");
        html.append("  <p><a href=\"/xwiki/bin/view/").append(DEFAULT_HOME_PAGE).append(TAG_END)
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

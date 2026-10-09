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
 * A bookmark pointing to a wiki page.
 * <p>
 * This is a mutable bean so that it can be (de)serialized as JSON.
 *
 * @version $Id$
 * @since 1.0
 */
public class BookmarkEntry
{
    private String page;

    private String label;

    private String folder = BookmarkFolder.ROOT;

    private int order;

    private boolean panel;

    /**
     * @return the serialized reference of the bookmarked page, including its wiki
     */
    public String getPage()
    {
        return this.page;
    }

    /**
     * @param page the serialized reference of the bookmarked page
     */
    public void setPage(String page)
    {
        this.page = page;
    }

    /**
     * @return the label chosen by the user, possibly empty
     */
    public String getLabel()
    {
        return this.label;
    }

    /**
     * @param label the label chosen by the user
     */
    public void setLabel(String label)
    {
        this.label = label;
    }

    /**
     * @return the containing folder identifier, {@link BookmarkFolder#ROOT} when uncategorized
     */
    public String getFolder()
    {
        return this.folder;
    }

    /**
     * @param folder the containing folder identifier, {@link BookmarkFolder#ROOT} when uncategorized
     */
    public void setFolder(String folder)
    {
        this.folder = folder;
    }

    /**
     * @return the position within the folder
     */
    public int getOrder()
    {
        return this.order;
    }

    /**
     * @param order the position within the folder
     */
    public void setOrder(int order)
    {
        this.order = order;
    }

    /**
     * @return whether the user selected this bookmark for the panel
     */
    public boolean isPanel()
    {
        return this.panel;
    }

    /**
     * @param panel whether the user selected this bookmark for the panel
     */
    public void setPanel(boolean panel)
    {
        this.panel = panel;
    }
}

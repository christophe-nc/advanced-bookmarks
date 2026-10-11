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
package org.xwiki.contrib.advancedbookmarks.test.po;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * The {@code Panels.MyBookmarks} panel, as displayed in the page layout.
 *
 * @version $Id$
 */
public class BookmarksPanel extends BaseElement
{
    private static final String LIST = "ul.advanced-bookmarks-panel-list";

    /**
     * @return the texts of the bookmarks listed by the panel, in order
     */
    public List<String> getItems()
    {
        return getTexts(By.cssSelector(LIST + " > li"));
    }

    /**
     * @return the texts of the bookmarks displayed as deleted pages
     */
    public List<String> getDeletedItems()
    {
        return getTexts(By.cssSelector(LIST + " .advanced-bookmarks-panel-deleted"));
    }

    /**
     * Follows the link to the management page.
     *
     * @return the management page
     */
    public BookmarksManager clickViewAll()
    {
        getDriver().findElement(By.cssSelector(".advanced-bookmarks-panel-all a")).click();
        return new BookmarksManager();
    }

    private List<String> getTexts(By locator)
    {
        return getDriver().findElementsWithoutWaiting(locator).stream().map(WebElement::getText).map(String::trim)
            .toList();
    }
}

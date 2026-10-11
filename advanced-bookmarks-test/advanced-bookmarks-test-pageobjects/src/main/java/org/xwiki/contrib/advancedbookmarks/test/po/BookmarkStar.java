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
 * The star button that Advanced Bookmarks adds to the action bar of the current page.
 *
 * @version $Id$
 */
public class BookmarkStar extends BaseElement
{
    private static final By BUTTON = By.cssSelector("#tmAdvancedBookmarks button");

    /**
     * @return {@code true} when the star is displayed on the current page
     */
    public boolean isDisplayed()
    {
        List<WebElement> buttons = getDriver().findElementsWithoutWaiting(BUTTON);
        return !buttons.isEmpty() && buttons.get(0).isDisplayed();
    }

    /**
     * @return {@code true} when the current page is bookmarked, as announced to assistive technologies
     */
    public boolean isBookmarked()
    {
        return Boolean.parseBoolean(getButton().getAttribute("aria-pressed"));
    }

    /**
     * Clicks the star and waits until its state has changed, that is until the server has answered.
     */
    public void toggle()
    {
        boolean expected = !isBookmarked();
        getButton().click();
        getDriver().waitUntilCondition(driver -> isBookmarked() == expected);
    }

    private WebElement getButton()
    {
        return getDriver().findElement(BUTTON);
    }
}

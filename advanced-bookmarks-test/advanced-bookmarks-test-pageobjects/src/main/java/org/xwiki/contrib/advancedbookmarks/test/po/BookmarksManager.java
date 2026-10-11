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
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;
import org.xwiki.test.ui.po.BaseElement;

/**
 * The bookmark management interface displayed by the {@code {{bookmarks/}}} macro (management page, profile tab).
 *
 * @version $Id$
 */
public class BookmarksManager extends BaseElement
{
    private static final By CONTAINER = By.cssSelector(".advanced-bookmarks-manager");

    // The tree of bookmarks, or the message displayed instead when there is none.
    private static final By LOADED = By.cssSelector(
        ".advanced-bookmarks-manager .advanced-bookmarks-tree, "
            + ".advanced-bookmarks-manager .advanced-bookmarks-message");

    private static final By FOLDER_NAMES = By.cssSelector(".advanced-bookmarks-folder-name");

    private static final By USAGE_METER = By.cssSelector(".advanced-bookmarks-usage meter");

    /**
     * @return {@code true} when the management interface is displayed, {@code false} when the macro displayed a
     *     message instead (guest, user not allowed)
     */
    public boolean isDisplayed()
    {
        return !getDriver().findElementsWithoutWaiting(CONTAINER).isEmpty();
    }

    /**
     * @return {@code true} when the macro displayed an error message, such as the refusal to show the bookmarks of
     *     another user
     */
    public boolean hasErrorMessage()
    {
        return !getDriver().findElementsWithoutWaiting(By.cssSelector(".box.errormessage")).isEmpty();
    }

    /**
     * Waits until the bookmarks are loaded and displayed.
     *
     * @return this page object
     */
    public BookmarksManager waitUntilLoaded()
    {
        getDriver().waitUntilElementIsVisible(LOADED);
        return this;
    }

    /**
     * @return the owner of the managed bookmarks, as announced by the macro
     */
    public String getUser()
    {
        return getDriver().findElement(CONTAINER).getAttribute("data-user");
    }

    /**
     * @return {@code true} when the user picker of administrators is displayed
     */
    public boolean hasUserPicker()
    {
        return !getDriver().findElementsWithoutWaiting(By.cssSelector(".advanced-bookmarks-user-picker")).isEmpty();
    }

    /**
     * Creates a folder at the root, with the given name, typed in the inline field opened by the button.
     *
     * @param name the name of the folder
     */
    public void createFolder(String name)
    {
        getDriver().findElement(By.cssSelector("button[data-action='newFolder']")).click();
        // The page focuses the field and selects the default name. The keys are sent to the focused element, as a user
        // types them: clear() or sendKeys() on the element run the focusing steps of WebDriver, and the field is
        // validated, then removed, as soon as it loses the focus.
        getDriver().waitUntilCondition(driver -> String.valueOf(driver.switchTo().activeElement().getAttribute("class"))
            .contains("advanced-bookmarks-edit"));
        new Actions(getDriver().getWrappedDriver()).keyDown(Keys.CONTROL).sendKeys("a").keyUp(Keys.CONTROL)
            .sendKeys(name).sendKeys(Keys.ENTER).perform();
        getDriver().waitUntilCondition(driver -> getFolderNames().contains(name));
    }

    /**
     * @return the names of the folders, in display order
     */
    public List<String> getFolderNames()
    {
        return getDriver().findElementsWithoutWaiting(FOLDER_NAMES).stream().map(WebElement::getText)
            .map(String::trim).toList();
    }

    /**
     * Moves a bookmark to a folder with the keyboard-friendly "Move to" list.
     *
     * @param page the full reference of the bookmarked page
     * @param folderName the name of the target folder
     */
    public void moveBookmark(String page, String folderName)
    {
        String folderId = getDriver().findElements(By.cssSelector(".advanced-bookmarks-folder-header")).stream()
            .filter(header -> folderName.equals(header.findElement(FOLDER_NAMES).getText().trim()))
            .map(header -> header.getAttribute("data-folder")).findFirst().orElseThrow();
        new Select(getBookmarkItem(page).findElement(By.cssSelector("select[data-action='moveBookmark']")))
            .selectByValue(folderId);
    }

    /**
     * Selects or unselects a bookmark for the panel.
     *
     * @param page the full reference of the bookmarked page
     */
    public void togglePanel(String page)
    {
        getBookmarkItem(page).findElement(By.cssSelector("input[data-action='panel']")).click();
    }

    /**
     * @return the space used by the bookmarks, in bytes, as displayed by the usage indicator
     */
    public double getUsedSpace()
    {
        return Double.parseDouble(getDriver().findElement(USAGE_METER).getDomProperty("value"));
    }

    /**
     * @return the text of the usage indicator, such as "Space used: 42%"
     */
    public String getUsageText()
    {
        return getDriver().findElement(By.cssSelector(".advanced-bookmarks-usage label")).getText().trim();
    }

    private WebElement getBookmarkItem(String page)
    {
        return getDriver().findElements(By.cssSelector("li.advanced-bookmarks-bookmark")).stream()
            .filter(item -> page.equals(item.getAttribute("data-page"))).findFirst().orElseThrow();
    }
}

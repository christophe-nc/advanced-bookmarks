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
package org.xwiki.contrib.advancedbookmarks.test.ui;

import java.util.List;

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Administration actions shared by the functional tests: they need the wiki administrator.
 *
 * @version $Id$
 */
final class AdminActions
{
    private static final String CONFIGURATION_CLASS = "AdvancedBookmarks.Code.ConfigurationClass";

    private AdminActions()
    {
    }

    /**
     * Logs in as the wiki administrator, created if needed.
     *
     * @param setup the test utilities
     */
    static void loginAsAdmin(TestUtils setup)
    {
        // Creating the administrator sets rights through REST with the credentials of the current session, which may
        // be those of a simple user left by a previous test: the superadmin always has the needed rights.
        setup.loginAsSuperAdmin();
        setup.createAdminUser();
        setup.loginAsAdmin();
    }

    /**
     * Renames a page with the refactoring module, as XWiki does from the rename dialog, and waits for the end of the
     * job. The administrator must be logged in.
     *
     * @param setup the test utilities
     * @param source the page to rename
     * @param newName the new name of the page, in the same space
     */
    static void rename(TestUtils setup, DocumentReference source, String newName)
    {
        DocumentReference script = new DocumentReference(source.getWikiReference().getName(), "AdvancedBookmarksTests",
            "Rename" + source.getName());
        String content = String.format("{{velocity}}%n"
            + "#set ($source = $services.model.resolveDocument('%s'))%n"
            + "#set ($job = $services.refactoring.rename($source, '%s'))%n"
            + "#set ($discard = $job.join())%n"
            + "rename-$job.status.state%n"
            + "{{/velocity}}", source.getLastSpaceReference().getName() + '.' + source.getName(), newName);
        String result = setup.createPage(script, content, "Rename").getContent();
        assertTrue(result.contains("rename-FINISHED"), result);
        setup.deletePage(script);
    }

    /**
     * Adds the bookmarks panel to the right column of the wiki layout. The administrator must be logged in.
     *
     * @param setup the test utilities
     */
    static void showBookmarksPanel(TestUtils setup)
    {
        setup.updateObject("XWiki", "XWikiPreferences", "XWiki.XWikiPreferences", 0, "rightPanels",
            "Panels.MyBookmarks", "showRightPanels", "1");
    }

    /**
     * Changes the configuration of Advanced Bookmarks. The administrator must be logged in.
     *
     * @param setup the test utilities
     * @param properties the configuration properties and their values
     */
    static void configure(TestUtils setup, Object... properties)
    {
        // The spaces are given as a list: a single "AdvancedBookmarks.Code" would be the name of one space.
        setup.updateObject(List.of("AdvancedBookmarks", "Code"), "Configuration", CONFIGURATION_CLASS, 0, properties);
    }

    /**
     * Restores the default configuration of Advanced Bookmarks. The administrator must be logged in.
     *
     * @param setup the test utilities
     */
    static void resetConfiguration(TestUtils setup)
    {
        configure(setup, "panelLimit", "10", "starEnabled", "1", "excludedPages", "");
    }
}

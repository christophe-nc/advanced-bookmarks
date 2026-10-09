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
 * Exposes the public extension coordinates.
 *
 * @version $Id$
 * @since 1.0
 */
public class ExtensionMetadata
{
    /** Maven group identifier. */
    public static final String GROUP_ID = "org.xwiki.contrib";
    /** Maven artifact identifier. */
    public static final String ARTIFACT_ID = "advanced-bookmarks";
    /** Extension display name. */
    public static final String NAME = "Advanced Bookmarks";
    /** Extension license. */
    public static final String LICENSE = "LGPL 2.1";

    /**
     * @return the Maven group identifier
     */
    public String getGroupId()
    {
        return GROUP_ID;
    }

    /**
     * @return the Maven artifact identifier
     */
    public String getArtifactId()
    {
        return ARTIFACT_ID;
    }

    /**
     * @return the extension display name
     */
    public String getName()
    {
        return NAME;
    }

    /**
     * @return the extension license
     */
    public String getLicense()
    {
        return LICENSE;
    }

    /**
     * @return the Maven coordinates
     */
    public String getCoordinates()
    {
        return GROUP_ID + ":" + ARTIFACT_ID;
    }
}

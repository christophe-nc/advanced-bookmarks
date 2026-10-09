package org.xwiki.contrib.advancedbookmarks;

public class ExtensionMetadata {
    public static final String GROUP_ID = "org.xwiki.contrib";
    public static final String ARTIFACT_ID = "advanced-bookmarks";
    public static final String NAME = "Advanced Bookmarks";
    public static final String LICENSE = "LGPL 2.1";

    public String getGroupId() {
        return GROUP_ID;
    }

    public String getArtifactId() {
        return ARTIFACT_ID;
    }

    public String getName() {
        return NAME;
    }

    public String getLicense() {
        return LICENSE;
    }

    public String getCoordinates() {
        return GROUP_ID + ":" + ARTIFACT_ID;
    }
}

package org.dwbn.plugins.playlist;

public class TrackRemovalItem {
    public int trackIndex = -1;
    public String trackId = "";

    TrackRemovalItem(int index, String id) {
        trackIndex = index;
        trackId = id;
    }
}


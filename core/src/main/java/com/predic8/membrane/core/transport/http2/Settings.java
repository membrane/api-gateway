package com.predic8.membrane.core.transport.http2;

public class Settings {
    private volatile int maxFrameSize = 16384;
    private int headerTableSize = 4096;
    private int maxConcurrentStreams = -1; // initially, there is no limit
    private int initialWindowSize = 65535;
    private int maxHeaderListSize = -1; // initially, there is no limit
    private int enablePush = 1;

    /**
     * PeerSettings: Called on various threads.
     */
    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    /**
     * PeerSettings: Called from the receiver thread.
     */
    public void setMaxFrameSize(int value) {
        maxFrameSize = value;
    }

    public int getEnablePush() {
        return enablePush;
    }

    public void setEnablePush(int enablePush) {
        this.enablePush = enablePush;
    }

    public int getHeaderTableSize() {
        return headerTableSize;
    }

    public void setHeaderTableSize(int headerTableSize) {
        this.headerTableSize = headerTableSize;
    }

    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    public void setMaxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    public int getInitialWindowSize() {
        return initialWindowSize;
    }

    public void setInitialWindowSize(int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
    }

    public int getMaxHeaderListSize() {
        return maxHeaderListSize;
    }

    public void setMaxHeaderListSize(int maxHeaderListSize) {
        this.maxHeaderListSize = maxHeaderListSize;
    }
}

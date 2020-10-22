/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http2;

public class Settings {
    private volatile int maxFrameSize = 16384;
    private int headerTableSize = 4096;
    private int maxConcurrentStreams = -1; // initially, there is no limit
    private int initialWindowSize = 65535;
    private int maxHeaderListSize = -1; // initially, there is no limit
    private int enablePush = 1;

    public Settings() {
    }

    public void copyFrom(Settings settings) {
        setMaxFrameSize(settings.getMaxFrameSize());
        setHeaderTableSize(settings.getHeaderTableSize());
        setMaxConcurrentStreams(settings.getMaxConcurrentStreams());
        setInitialWindowSize(settings.getInitialWindowSize());
        setMaxHeaderListSize(settings.getMaxHeaderListSize());
        setEnablePush(settings.getEnablePush());
    }

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

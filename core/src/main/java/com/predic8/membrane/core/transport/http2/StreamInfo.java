package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.transport.http2.frame.HeaderBlockFragment;
import com.predic8.membrane.core.transport.http2.frame.HeadersFrame;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class StreamInfo {

    public long peerWindowSize;
    public StreamState state = StreamState.IDLE;

    public StreamInfo(Settings settings) {
        peerWindowSize = settings.getInitialWindowSize();
    }

}

package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http2.frame.Frame;
import com.twitter.hpack.Encoder;

import java.io.IOException;
import java.util.List;

public interface FrameProducer {
    List<Frame> call(Encoder encoder, Settings sendSettings) throws IOException;
}

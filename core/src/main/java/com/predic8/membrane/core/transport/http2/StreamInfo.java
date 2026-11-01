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

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http2.frame.DataFrame;
import com.predic8.membrane.core.transport.http2.frame.FatalConnectionException;
import com.predic8.membrane.core.transport.http2.frame.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.transport.http2.frame.Error.ERROR_PROTOCOL_ERROR;
import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_DATA;
import static com.predic8.membrane.core.transport.http2.frame.HeadersFrame.FLAG_END_STREAM;

public class StreamInfo {
    private static final Logger log = LoggerFactory.getLogger(StreamInfo.class);

    /** Queued behind the received DATA frames to tell the body reader that no more will arrive. */
    private static final DataFrame ABORTED = createAbortedMarker();

    private final FrameSender sender;

    private final FlowControl flowControl;
    private final PeerFlowControl peerFlowControl;
    private final LinkedTransferQueue<DataFrame> dataFramesReceived = new LinkedTransferQueue<>();
    private final int streamId;
    private final List<StreamInfo> priorityChildren = new ArrayList<>();
    private final LinkedTransferQueue<Frame> dataFramesToBeSent = new LinkedTransferQueue<>();
    private final Semaphore bufferedDataFrames = new Semaphore(4);
    private StreamInfo priorityParent = null;
    private StreamState state = StreamState.IDLE;
    private int weight;
    private Message message;
    private boolean isTrailer;
    private volatile IOException abortCause;
    /** The content-length the message declares, or -1. Accessed on the receiver thread only. */
    private long expectedContentLength = -1;
    private long receivedContentLength;

    public StreamInfo(int streamId, FrameSender sender, Settings peerSettings, Settings ourSettings) {
        this.streamId = streamId;
        this.sender = sender;
        if (streamId == 0) {
            peerFlowControl = null;
            flowControl = null;
        } else {
            peerFlowControl = new PeerFlowControl(streamId, sender, peerSettings);
            flowControl = new FlowControl(streamId, sender, ourSettings);
        }

    }

    public void receivedDataFrame(DataFrame df) {
        dataFramesReceived.add(df);
        flowControl.received(df.getFrame().getLength());

        if (df.isEndStream()) {
            receivedEndStream(true);
        }
    }

    void expectContentLength(long contentLength) {
        expectedContentLength = contentLength;
    }

    /**
     * Counts received DATA against the declared content-length.
     *
     * @return false if more DATA arrived than declared, or the stream ends with less. Only once: the stream gets
     *         reset then, so DATA still in flight is not checked anymore.
     */
    boolean keepsContentLength(int dataLength, boolean endStream) {
        if (expectedContentLength < 0)
            return true;
        receivedContentLength += dataLength;
        if (receivedContentLength <= expectedContentLength && (!endStream || receivedContentLength == expectedContentLength))
            return true;
        expectedContentLength = -1;
        return false;
    }

    public DataFrame removeDataFrame() throws IOException {
        try {
            DataFrame dataFrame = dataFramesReceived.poll(1, TimeUnit.MINUTES);
            if (dataFrame == ABORTED)
                throw abortCause;
            if (dataFrame != null) {
                flowControl.processed(dataFrame.getFrame().getLength());
            }
            return dataFrame;
        } catch (InterruptedException e) {
            // Returning null would make the body reader poll again, which fails immediately: it would spin.
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for DATA on HTTP/2 stream " + streamId);
        }
    }

    /**
     * Makes the body reader fail once it has consumed the DATA frames received so far, as no more will
     * arrive, e.g. because the stream was reset or the connection was closed. Called on the receiver thread.
     */
    void abortReceiving(IOException cause) {
        if (abortCause != null)
            return;
        abortCause = cause;
        dataFramesReceived.add(ABORTED);
    }

    private static DataFrame createAbortedMarker() {
        final var frame = new Frame();
        frame.fill(TYPE_DATA, 0, 0, null, 0, 0);
        return frame.asData();
    }

    public AbstractBody createBody() {
        return new Http2Body();
    }

    /** Wakes this stream's writers waiting to send DATA, after the frame sender stopped. */
    void senderStopped() {
        bufferedDataFrames.release();
        if (peerFlowControl != null)
            peerFlowControl.senderStopped();
    }

    public PeerFlowControl getPeerFlowControl() {
        return peerFlowControl;
    }

    public int getStreamId() {
        return streamId;
    }

    public synchronized void receivedRstStream(int errorCode) {
        if (state == StreamState.IDLE)
            return;
        abortReceiving(new IOException("HTTP/2 stream %d was reset by the peer with error code %d.".formatted(streamId, errorCode)));
        setState(StreamState.CLOSED);
        // TODO: stop sending frames
    }

    public synchronized void receivedEndStream(boolean fromDataFrame) {
        if (state == StreamState.OPEN)
            setState(StreamState.HALF_CLOSED_REMOTE);
        if (state == StreamState.HALF_CLOSED_LOCAL)
            setState(StreamState.CLOSED);

        if (!fromDataFrame) {
            // fake 0-length data frame so that the body is terminated
            Frame frame = new Frame();
            frame.fill(TYPE_DATA,
                    FLAG_END_STREAM,
                    streamId,
                    null,
                    0,
                    0);
            dataFramesReceived.add(frame.asData());
        }
    }

    public synchronized void receivedHeaders() throws IOException {
        if (state != StreamState.IDLE && state != StreamState.RESERVED_REMOTE && state != StreamState.RESERVED_LOCAL && state != StreamState.OPEN && state != StreamState.HALF_CLOSED_LOCAL)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        if (state == StreamState.IDLE)
            setState(StreamState.OPEN);

        if (state == StreamState.RESERVED_REMOTE)
            setState(StreamState.HALF_CLOSED_LOCAL);

        isTrailer = true;
    }

    /** The next HEADERS frame carries the final response rather than a trailer. */
    void receivedInterimResponse() {
        isTrailer = false;
        message = null;
    }

    private void setState(StreamState state) {
        this.state = state;

        // detach message, since no trailers can follow, once the CLOSED state has been reached
        if (state == StreamState.CLOSED) {
            message = null;
            // Within the synchronized transition, so whoever sees CLOSED also sees the stream removed.
            sender.streamClosed(this);
        }

        log.debug("streamId=" + streamId + " changed state to " + state);
    }

    public void receivedPriority() {
        // nothing to do
    }

    public synchronized void sendRstStream() {
        if (state == StreamState.IDLE)
            return;

        setState(StreamState.CLOSED);
    }

    public synchronized void sendHeaders() {
        if (state == StreamState.IDLE)
            setState(StreamState.OPEN);
        if (state == StreamState.RESERVED_LOCAL)
            setState(StreamState.HALF_CLOSED_REMOTE);
    }

    public synchronized void sendEndStream() {
        if (state == StreamState.OPEN)
            setState(StreamState.HALF_CLOSED_LOCAL);
        if (state == StreamState.HALF_CLOSED_REMOTE)
            setState(StreamState.CLOSED);
    }

    private class Http2Body extends AbstractBody {

        long streamedLength = 0;
        Header trailer;

        @Override
        protected void readLocal() throws IOException {
            while(true) {
                DataFrame df = removeDataFrame();
                if (df == null)
                    continue;
                chunks.add(new Chunk(createByteArray(df)));

                if (df.isEndStream())
                    break;
            }
        }

        private byte[] createByteArray(DataFrame df) {
            // TODO this has bad performance, can be avoided by subclassing Chunk
            int len = df.getDataLength();
            byte[] buf = new byte[len];
            if (len > 0)
                System.arraycopy(df.getContent(), df.getDataStartIndex(), buf, 0, len);
            return buf;
        }

        @Override
        protected void writeAlreadyRead(AbstractBodyTransferer out) throws IOException {
            if (getLength() > 0) {
                byte[] content = getContent();
                out.write(content, 0, content.length);
            }
            out.finish(trailer);
        }

        @Override
        protected void writeStreamed(AbstractBodyTransferer out) {
            chunks.clear();
            while (true) {
                DataFrame df;
                try {
                    df = removeDataFrame();
                } catch (IOException e) {
                    throw fail(e);
                }
                if (df == null)
                    continue;
                int len = df.getDataLength();
                if (len > 0) {
                    try {
                        out.write(df.getContent(), df.getDataStartIndex(), len);
                    } catch (IOException e) {
                        throw new WritingBodyException(e);
                    }
                    streamedLength += len;
                }

                if (df.isEndStream())
                    break;
            }
            try {
                out.finish(trailer);
            } catch (IOException e) {
                throw new WritingBodyException(e);
            }
            markAsRead();
        }

        @Override
        protected byte[] getRawLocal() {
            if (chunks.isEmpty()) {
                log.debug("Chunks list is empty (hash={})", hashCode());
                return new byte[0];
            }

            return getContent();
        }

        @Override
        public long getLength() {
            if (wasStreamed())
                return streamedLength;
            return super.getLength();
        }

        @Override
        public boolean hasTrailer() {
            return trailer != null;
        }

        @Override
        public Header getTrailer() {
            return trailer;
        }

        @Override
        public boolean setTrailer(Header trailer) {
            this.trailer = trailer;
            return true;
        }
    }

    public List<StreamInfo> getPriorityChildren() {
        return priorityChildren;
    }

    public StreamInfo getPriorityParent() {
        return priorityParent;
    }

    public void setPriorityParent(StreamInfo priorityParent) {
        this.priorityParent = priorityParent;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    /**
     * Do not use within the logic part.
     */
    public synchronized StreamState getState() {
        return state;
    }

    public LinkedTransferQueue<Frame> getDataFramesToBeSent() {
        return dataFramesToBeSent;
    }

    public Semaphore getBufferedDataFrames() {
        return bufferedDataFrames;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public boolean isTrailer() {
        return isTrailer;
    }

    @Override
    public String toString() {
        return "StreamInfo{" +
                "streamId=" + streamId +
                ", state=" + state +
                '}';
    }
}

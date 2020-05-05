package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.AbstractBodyTransferrer;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.transport.http2.frame.DataFrame;
import com.predic8.membrane.core.transport.http2.frame.FatalConnectionException;
import com.predic8.membrane.core.transport.http2.frame.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.transport.http2.frame.Error.ERROR_PROTOCOL_ERROR;

public class StreamInfo {
    private static final Logger log = LoggerFactory.getLogger(StreamInfo.class);

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

    public StreamInfo(int streamId, Http2ServerHandler h2sh) {
        this.streamId = streamId;
        if (streamId == 0) {
            peerFlowControl = null;
            flowControl = null;
        } else {
            peerFlowControl = new PeerFlowControl(streamId, h2sh.getSender(), h2sh.getPeerSettings());
            flowControl = new FlowControl(streamId, h2sh.getSender(), h2sh.getOurSettings());
        }

    }

    public void receivedDataFrame(DataFrame df) {
        dataFramesReceived.add(df);
        flowControl.received(df.getFrame().getLength());

        if (df.isEndStream()) {
            receivedEndStream();
        }
    }

    public DataFrame removeDataFrame() throws IOException {
        try {
            DataFrame dataFrame = dataFramesReceived.poll(1, TimeUnit.MINUTES);
            if (dataFrame != null) {
                flowControl.processed(dataFrame.getFrame().getLength());
            }
            return dataFrame;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public AbstractBody createBody() {
        return new Http2Body();
    }

    public PeerFlowControl getPeerFlowControl() {
        return peerFlowControl;
    }

    public int getStreamId() {
        return streamId;
    }

    public synchronized void receivedRstStream() {
        if (state == StreamState.IDLE)
            return;
        setState(StreamState.CLOSED);
        // TODO: stop sending frames
    }

    public synchronized void receivedEndStream() {
        if (state == StreamState.OPEN)
            setState(StreamState.HALF_CLOSED_REMOTE);
        if (state == StreamState.HALF_CLOSED_LOCAL)
            setState(StreamState.CLOSED);
    }

    public synchronized void receivedHeaders() throws IOException {
        if (state != StreamState.IDLE && state != StreamState.RESERVED_REMOTE && state != StreamState.RESERVED_LOCAL && state != StreamState.OPEN)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        if (state == StreamState.IDLE)
            setState(StreamState.OPEN);

        if (state == StreamState.RESERVED_REMOTE)
            setState(StreamState.HALF_CLOSED_LOCAL);
    }

    private void setState(StreamState state) {
        this.state = state;

        log.info("streamId=" + streamId + " changed state to " + state);
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
            byte[] buf = new byte[df.getDataLength()];
            System.arraycopy(df.getContent(), df.getDataStartIndex(), buf, 0, df.getDataLength());
            return buf;
        }

        @Override
        protected void writeAlreadyRead(AbstractBodyTransferrer out) throws IOException {
            if (getLength() == 0)
                return;

            out.write(getContent(), 0, getLength());
            out.finish();
        }

        @Override
        protected void writeNotRead(AbstractBodyTransferrer out) throws IOException {
            chunks.clear();
            while (true) {
                DataFrame df = removeDataFrame();
                if (df == null)
                    continue;
                out.write(df.getContent(), df.getDataStartIndex(), df.getDataLength());
                chunks.add(new Chunk(createByteArray(df)));

                if (df.isEndStream())
                    break;
            }

            out.finish();
            markAsRead();
        }

        @Override
        protected void writeStreamed(AbstractBodyTransferrer out) throws IOException {
            chunks.clear();
            while (true) {
                DataFrame df = removeDataFrame();
                if (df == null)
                    continue;
                out.write(df.getContent(), df.getDataStartIndex(), df.getDataLength());

                if (df.isEndStream())
                    break;
            }
            out.finish();
            markAsRead();
        }

        @Override
        protected byte[] getRawLocal() throws IOException {
            if (chunks.isEmpty()) {
                log.debug("size of chunks list: " + chunks.size() + "  " + hashCode());
                log.debug("chunks size is: " + chunks.size() + " at time: " + System.currentTimeMillis());
                return new byte[0];
            }

            return getContent();
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
}

package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.AbstractBodyTransferrer;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.transport.http2.frame.DataFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

public class StreamInfo {
    private static final Logger log = LoggerFactory.getLogger(StreamInfo.class);

    private final FlowControl flowControl;
    private final PeerFlowControl peerFlowControl;
    public StreamState state = StreamState.IDLE;

    private LinkedTransferQueue<DataFrame> dataFrames = new LinkedTransferQueue<>();

    public StreamInfo(int streamId, Http2ServerHandler h2sh) {
        peerFlowControl = new PeerFlowControl(streamId, h2sh.getSender(), h2sh.getPeerSettings());
        flowControl = new FlowControl(streamId, h2sh.getSender(), h2sh.getOurSettings());
    }

    public void addDataFrame(DataFrame df) {
        dataFrames.add(df);
        flowControl.received(df.getDataLength());
    }

    public DataFrame removeDataFrame() throws IOException {
        try {
            DataFrame dataFrame = dataFrames.poll(1, TimeUnit.MINUTES);
            if (dataFrame != null) {
                flowControl.processed(dataFrame.getDataLength());
            }
            return dataFrame;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public AbstractBody createBody() {
        return new AbstractBody() {
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
                // TODO this has bad performance
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
        };
    }

    public PeerFlowControl getPeerFlowControl() {
        return peerFlowControl;
    }
}

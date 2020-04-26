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

    public long peerWindowSize;
    public StreamState state = StreamState.IDLE;

    LinkedTransferQueue<DataFrame> dataFrames = new LinkedTransferQueue<>();

    public StreamInfo(Settings settings) {
        peerWindowSize = settings.getInitialWindowSize();
    }

    public AbstractBody createBody() {
        return new AbstractBody() {
            @Override
            protected void readLocal() throws IOException {
                while(true) {
                    DataFrame df = null;
                    try {
                        df = dataFrames.poll(1, TimeUnit.MINUTES);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
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
                    DataFrame df = null;
                    try {
                        df = dataFrames.poll(1, TimeUnit.MINUTES);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
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
                    DataFrame df = null;
                    try {
                        df = dataFrames.poll(1, TimeUnit.MINUTES);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
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
}

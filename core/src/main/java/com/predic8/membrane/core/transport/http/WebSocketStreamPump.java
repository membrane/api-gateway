package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketFrameAssembler;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptor;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class WebSocketStreamPump extends StreamPump {

    //pumpsToRight = from sender to recipient "sender -> recipient"
    public WebSocketStreamPump(InputStream in, OutputStream out, StreamPumpStats stats, String name, Rule rule, boolean pumpsToRight) {
        super(in, out, stats, name, rule);
        this.pumpsToRight = pumpsToRight;
        frameAssembler = new WebSocketFrameAssembler(in);
    }

    public void init(WebSocketStreamPump otherStreamPump){
        this.otherStreamPump = otherStreamPump;
    }

    List<WebSocketInterceptor> chain = new ArrayList<>(); // TODO: initialize
    WebSocketStreamPump otherStreamPump;
    private final boolean pumpsToRight;
    boolean connectionIsOpen = true;
    WebSocketFrameAssembler frameAssembler;

    @Override
    public void run() {
        if(otherStreamPump == null)
            throw new RuntimeException("Call init with other WebSocketStreamPump (backward direction)");
        //if (stats != null)
        //    stats.registerPump(this);
        try {
                frameAssembler.getNextFrame(frame -> {
                    try {
                        if (pumpsToRight) {
                            System.out.println("pumptoright");
                            passFrameToChainElement(0, true, frame);
                        } else {
                            System.out.println("pumptoleft");
                            passFrameToChainElement(chain.size() - 1, false, frame);
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                });

                // pass frame to WSInterceptor chain

            /*
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
                out.flush();
                if (stats != null)
                    bytesTransferred.addAndGet(length);
            }
            */
        //} catch (SocketTimeoutException e) {
            // do nothing
        //} catch (SocketException e) {
            // do nothing
        //} catch (SSLException e) {
            // do nothing
        //} catch (IOException e) {
            //log.error("Reading from or writing to stream failed: " + e);
        } catch (Exception e) {
            connectionIsOpen = false;
            //e.printStackTrace();
        } finally {
            try {
                out.close();
            } catch (Exception e) {
                // ignore
            }
            //if (stats != null)
            //    stats.unregisterPump(this);
        }
    }

    private void passFrameToChainElement(int i, boolean frameTravelsToRight, WebSocketFrame frame) throws Exception {
        if(chain.isEmpty()){
            if (true)
                synchronized (out) {
                    frame.write(out);
                }
            return;
        }
        if (i == -1) {
            synchronized (otherStreamPump.out) {
                frame.write(otherStreamPump.out);
            }
        } else if (chain.size() == i) {
            // write frame to out
            synchronized (out) {
                frame.write(out);
            }
        } else {
            chain.get(i).handleFrame(frame, frameTravelsToRight, frame1 -> {
                passFrameToChainElement(i + (frameTravelsToRight ? 1 : -1), frameTravelsToRight, frame1);
            });
        }
    }

}

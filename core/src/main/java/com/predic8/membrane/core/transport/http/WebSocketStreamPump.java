/* Copyright 2017 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.tunnel.WebSocketInterceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketFrameAssembler;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class WebSocketStreamPump extends StreamPump {

    //pumpsToRight == true: from sender to recipient "sender -> recipient"
    public WebSocketStreamPump(InputStream in, OutputStream out, StreamPumpStats stats, String name, Rule rule, boolean pumpsToRight) {
        super(in, out, stats, name, rule);
        this.pumpsToRight = pumpsToRight;
        frameAssembler = new WebSocketFrameAssembler(in);
        for (Interceptor i : rule.getInterceptors()) {
            if (i instanceof WebSocketInterceptor) {
                chain = ((WebSocketInterceptor) i).getInterceptors();
                for (WebSocketInterceptorInterface i2 : chain)
                    try {
                        i2.init(i.getRouter());
                    } catch (Exception e) {
                        log.error("Could not init WebSocketInterceptors:" + e.getMessage());
                    }
                break;
            }
        }

    }

    public void init(WebSocketStreamPump otherStreamPump) {
        this.otherStreamPump = otherStreamPump;
    }

    List<WebSocketInterceptorInterface> chain = new ArrayList<>();
    WebSocketStreamPump otherStreamPump;
    private final boolean pumpsToRight;
    boolean connectionIsOpen = true;
    WebSocketFrameAssembler frameAssembler;

    @Override
    public void run() {
        if (otherStreamPump == null)
            throw new RuntimeException("Call init with other WebSocketStreamPump (backward direction)");
        //if (stats != null)
        //    stats.registerPump(this);
        try {
            frameAssembler.readFrames(frame -> {
                try {
                    if (pumpsToRight) {
                        //System.out.println("==client to server==");
                        passFrameToChainElement(0, true, frame);
                    } else {
                        //System.out.println("==server to client==");
                        passFrameToChainElement(chain.size() - 1, false, frame);
                    }
                } catch (Exception e) {
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
            e.printStackTrace();
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
        if (chain.isEmpty()) {
            if (true)
                synchronized (out) {
                    frame.write(out);
                }
            return;
        }
        if (i == -1) {
            OutputStream target = pumpsToRight ? otherStreamPump.out : out;
            synchronized (target) {
                frame.write(target);
            }
        } else if (chain.size() == i) {
            // write frame to out
            OutputStream target = pumpsToRight ? out : otherStreamPump.out;
            synchronized (target) {
                frame.write(target);
            }
        } else {
            chain.get(i).handleFrame(frame, frameTravelsToRight, frame1 -> {
                passFrameToChainElement(i + (frameTravelsToRight ? 1 : -1), frameTravelsToRight, frame1);
            });
        }
    }

}

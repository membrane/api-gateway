/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util;

import java.io.*;
import java.net.*;

public class NetworkUtil {

    public static int getFreePortEqualAbove(int port) throws IOException {
        for (int p = port; p <= 65535; p++) {
            try (ServerSocket ignored = new ServerSocket(p)) {
                return p;
            } catch (Exception ignored) {}
        }
        throw new IOException("Could not find a free port");
    }

    public static Pair<byte[],Integer> readUpTo1KbOfDataFrom(Socket sourceSocket, byte[] buffer) throws IOException {
        int available = sourceSocket.getInputStream().available();
        int offset = 0;
        while(available > 0){
            if(available > buffer.length-offset){
                available = buffer.length-offset;

                //noinspection ResultOfMethodCallIgnored
                sourceSocket.getInputStream().read(buffer,offset,available);
                offset += available;
                break;
            }else {
                sourceSocket.getInputStream().read(buffer, offset, available);
                offset += available;
                available = sourceSocket.getInputStream().available();
            }
        }
        return new Pair<>(buffer,offset);
    }

}

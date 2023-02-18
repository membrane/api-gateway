package com.predic8.membrane.core.util;

import java.io.*;
import java.net.*;

public class NetworkUtil {

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

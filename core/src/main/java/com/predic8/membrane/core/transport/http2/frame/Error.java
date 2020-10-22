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

package com.predic8.membrane.core.transport.http2.frame;

public class Error {
    public static final int ERROR_NO_ERROR = 0x0;
    public static final int ERROR_PROTOCOL_ERROR = 0x1;
    public static final int ERROR_INTERNAL_ERROR = 0x2;
    public static final int ERROR_FLOW_CONTROL_ERROR = 0x3;
    public static final int ERROR_SETTINGS_TIMEOUT = 0x4;
    public static final int ERROR_STREAM_CLOSED = 0x5;
    public static final int ERROR_FRAME_SIZE_ERROR = 0x6;
    public static final int ERROR_REFUSED_STREAM = 0x7;
    public static final int ERROR_CANCEL = 0x8;
    public static final int ERROR_COMPRESSION_ERROR = 0x9;
    public static final int ERROR_CONNECT_ERROR = 0xA;
    public static final int ERROR_ENHANCE_YOUR_CALM = 0xB;
    public static final int ERROR_INADEQUATE_SECURITY = 0xC;
    public static final int ERROR_HTTP_1_1_REQUIRED = 0xD;
    
    public String toString(int error) {
        switch (error) {
            case ERROR_NO_ERROR: return "NO_ERROR";
            case ERROR_PROTOCOL_ERROR: return "PROTOCOL_ERROR";
            case ERROR_INTERNAL_ERROR: return "INTERNAL_ERROR";
            case ERROR_FLOW_CONTROL_ERROR: return "FLOW_CONTROL_ERROR";
            case ERROR_SETTINGS_TIMEOUT: return "SETTINGS_TIMEOUT";
            case ERROR_STREAM_CLOSED: return "STREAM_CLOSED";
            case ERROR_FRAME_SIZE_ERROR: return "FRAME_SIZE_ERROR";
            case ERROR_REFUSED_STREAM: return "REFUSED_STREAM";
            case ERROR_CANCEL: return "CANCEL";
            case ERROR_COMPRESSION_ERROR: return "COMPRESSION_ERROR";
            case ERROR_CONNECT_ERROR: return "CONNECT_ERROR";
            case ERROR_ENHANCE_YOUR_CALM: return "ENHANCE_YOUR_CALM";
            case ERROR_INADEQUATE_SECURITY: return "INADEQUATE_SECURITY";
            case ERROR_HTTP_1_1_REQUIRED: return "HTTP_1_1_REQUIRED";
            default: throw new IllegalArgumentException();
        }
    }
    
}

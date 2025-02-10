/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.http;

import static com.predic8.membrane.core.Constants.*;

public class ChunksBuilder {

    private String msg = "";

    private ChunksBuilder() {}

    public static ChunksBuilder chunks() {
        return new ChunksBuilder();
    }

    public ChunksBuilder add(String chunk) {
        msg += Integer.toHexString( chunk.length()) + CRLF;
        msg += chunk + CRLF;
        return this;
    }

    public byte[] build() {
        return (msg + "0" + CRLF + CRLF).getBytes();
    }

}

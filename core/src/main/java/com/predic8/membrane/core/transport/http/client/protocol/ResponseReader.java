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

package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.ConnectionFactory.*;
import com.predic8.membrane.core.util.*;

import java.io.*;

/**
 * Responsible for converting the raw bytes arriving over an {@link OutgoingConnectionType}
 * into a high-level {@link Response} object.
 */
public interface ResponseReader {
    Response read(Exchange exchange, OutgoingConnectionType ct) throws EndOfStreamException, IOException;
}

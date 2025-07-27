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

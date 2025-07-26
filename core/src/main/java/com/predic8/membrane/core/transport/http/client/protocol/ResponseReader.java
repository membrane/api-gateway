package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.ConnectionFactory.*;
import com.predic8.membrane.core.util.*;

import java.io.*;

public interface ResponseReader {
    Response read(Exchange exchange, OutgoingConnectionType ct) throws EndOfStreamException, IOException;
}

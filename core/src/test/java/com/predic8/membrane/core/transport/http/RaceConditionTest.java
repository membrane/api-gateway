package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class RaceConditionTest {

    @Test
    public void raceConditionTest() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            Thread serverThread = new Thread(() -> {
                try {
                    Socket socket = serverSocket.accept();

                    // --- First request ---
                    socket.getInputStream().read(new byte[1024]);

                    // Write chunked response
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write("HTTP/1.1 200 OK\r\n".getBytes());
                    outputStream.write("Transfer-Encoding: chunked\r\n\r\n".getBytes());
                    outputStream.write("17\r\nNo Mapping Rule matched\r\n".getBytes());
                    outputStream.write("0\r\n\r\n".getBytes());
                    outputStream.flush();

                    // --- Second request ---
                    socket.getInputStream().read(new byte[1024]);

                    outputStream.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
                    outputStream.flush();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            serverThread.start();

            HttpClient client = new HttpClient();
            Exchange exc = new Request.Builder().get("http://localhost:" + port).buildExchange();
            Response res = client.call(exc).getResponse();

            // This triggers the bug: the body is not read, but the connection is released.
            res.getBody().read();

            client.call(new Request.Builder().get("http://localhost:" + port).buildExchange());

            serverThread.join();
        }
    }
}

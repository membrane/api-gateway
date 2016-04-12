/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

public class OAuth2TrustedClient {

    static String clientId = "abc";
    static String clientSecret = "def";
    static String tokenEndpoint = "http://localhost:2000/oauth2/token";
    static String target = "http://localhost:2002";

    public static void main(String[] args) throws Exception {
        System.out.println(sendRequestToTarget(getToken()));
    }

    private static String sendRequestToTarget(String authorizationHeaderValue) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", authorizationHeaderValue);

        return connection.getResponseMessage();
    }

    private static String getToken() throws Exception {
        return parseTokenRequestResponse(getTokenRequestResponse());
    }

    private static String parseTokenRequestResponse(String tokenRequestResponse) {

        // the parsing is done by removing unnecessary parts from the left side until token and token_type is reached

        String temp = tokenRequestResponse.replaceFirst(Pattern.quote("{\"access_token\":\""),"");
        String token = temp.split(Pattern.quote("\""))[0];

        temp = temp.replaceFirst(Pattern.quote(token + "\",\"token_type\":\""),"");
        String tokenType = temp.split(Pattern.quote("\""))[0];

        return tokenType + " " + token;
    }

    private static String getTokenRequestResponse() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(tokenEndpoint).openConnection();
        connection.setRequestMethod("POST");

        sendPostData(connection, createTokenRequestParameters());

        return readResponse(connection);
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();

        String line;
        while((line = in.readLine()) != null)
            response.append(line);
        in.close();

        return response.toString();
    }

    private static void sendPostData(HttpURLConnection connection, String urlParameters) throws IOException {
        connection.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();
    }

    private static String createTokenRequestParameters() {
        return "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
    }


}

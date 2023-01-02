/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.util;

import com.predic8.membrane.core.util.*;

import java.net.*;
import java.util.*;

public class UriUtil {

    public static String normalizeUri(String s) {
        return s.replaceAll("/+", "/");
    }

    public static String trimQueryString(String path) {
        int idx = path.indexOf('?');
        if (idx == -1)
            return path;
        return path.substring(0, path.indexOf('?'));
    }

    // TODO migrate to core
    public static Map<String, String> parseQueryString(String url) {

        int idxQM = url.indexOf('?');
        if (idxQM == -1)
            return new HashMap<>();

        String qs = url.substring(idxQM + 1);

        if (qs.length() == 0)
            return new HashMap<>();

        return splitQueryString(qs);
    }

    private static Map<String, String> splitQueryString(String qs) {
        Map<String, String> qparams = new HashMap<>();
        String[] pairs = qs.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            qparams.put(kv[0], kv[1]);
        }
        return qparams;
    }

    public static String getUrlWithoutPath(URL url) {
        StringBuilder urlWithoutPath = new StringBuilder();

        if (url.getProtocol() != null)
            urlWithoutPath.append(url.getProtocol()).append("://");
        if (url.getHost() != null)
            urlWithoutPath.append(url.getHost());

        urlWithoutPath.append(getPortString(url));
        return urlWithoutPath.toString();
    }

    private static String getPortString(URL url) {
        if (url.getPort() == 80 && url.getProtocol().equals("http"))
            return "";
        if (url.getPort() == 443 && url.getProtocol().equals("https"))
            return "";
        if (url.getPort() == -1)
            return "";
        return ":"+url.getPort();
    }

    public static String rewrite(String url, String scheme, String host, int port) throws MalformedURLException, URISyntaxException {
        StringBuilder sb = new StringBuilder();
        sb.append(scheme);
        sb.append("://");
        sb.append(host);
        if (nonDefaultPort(scheme, port)) {
            sb.append(":");
            sb.append(port);
        }

        String path = getPathFromURL(url);
        if (path != null) {
            sb.append(path);
        }

        return sb.toString() ;
    }

    private static boolean nonDefaultPort(String scheme, int port) {
        return !((port == 80 && scheme.equals("http")) || (port == 443 && scheme.equals("https")));
    }

    // TODO
    public static String getPathFromURL(String str) throws URISyntaxException {

        return new URIFactory().create(str).getPath();

//        try {
//            return new URL(str).getPath();
//        } catch (Exception e) {
//            return new URL("http://" + str).getPath();
//        }
    }
}

package com.predic8.membrane.core.openapi.util;

import io.swagger.v3.oas.models.servers.*;

import java.net.*;
import java.util.*;

public class PathUtils {

    public static String normalizeUri(String s) {
        return s.replaceAll("\\/+", "/");
    }

    public static String trimQueryString(String path) {
        int idx = path.indexOf('?');
        if (idx == -1)
            return path;
        return path.substring(0, path.indexOf('?'));
    }

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
        for (int i = 0; i < pairs.length; i++) {
            String[] kv = pairs[i].split("=");
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
}

package com.predic8.membrane.core.security;

public class ApiKeySecurityScheme implements SecurityScheme {

    /**
     * Name of the header, query or cookie parameter
     */
   public String name;

   public In in;

    /**
     * Location of the key
     */
    public enum In { QUERY, HEADER, COOKIE }

    public ApiKeySecurityScheme(In in, String name) {
        this.name = name;
        this.in = in;
    }

    @Override
    public String toString() {
        return "ApiKeySecurityScheme{" +
               "name='" + name + '\'' +
               '}';
    }
}

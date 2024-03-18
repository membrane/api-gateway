package com.predic8.membrane.core.security;

public class ApiKeySecurityScheme extends AbstractSecurityScheme {

    /**
     * Name of the header, query or cookie parameter
     */
   public String name;

   public enum In { HEADER, QUERY, COOKIE }

   public In in;

    /**
     *
     * @param in Location of the key
     * @param name Name of the header, query or cookie parameter
     */
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

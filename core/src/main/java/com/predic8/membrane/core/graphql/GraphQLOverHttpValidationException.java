package com.predic8.membrane.core.graphql;

public class GraphQLOverHttpValidationException extends RuntimeException {


    private final int statusCode;

    public GraphQLOverHttpValidationException(String message) {
        super(message);
        statusCode = 400;
    }

    public GraphQLOverHttpValidationException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

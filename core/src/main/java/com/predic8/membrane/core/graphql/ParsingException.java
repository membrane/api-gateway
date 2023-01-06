package com.predic8.membrane.core.graphql;

public class ParsingException extends Exception {
    private final long position;

    public ParsingException(String message, long position) {
        super(message + " (At position " + position + " of the document).");
        this.position = position;
    }

    public long getPosition() {
        return position;
    }
}

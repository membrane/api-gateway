package com.predic8.membrane.core.cli;

import org.apache.commons.cli.MissingOptionException;

public class MissingRequiredOptionException extends MissingOptionException {
    private final CliCommand command;

    public MissingRequiredOptionException(String message, CliCommand command) {
        super(message);
        this.command = command;
    }

    public CliCommand getCommand() {
      return command;
    }
}

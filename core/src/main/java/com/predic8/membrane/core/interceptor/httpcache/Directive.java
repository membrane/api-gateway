package com.predic8.membrane.core.interceptor.httpcache;

import java.util.Optional;

import static com.predic8.membrane.core.interceptor.httpcache.Directives.fromString;

public class Directive {
    private final Directives name;
    private DirectiveArgument<?> argument;

    Directive(Directives name) {
        this.name = name;
    }

    Directive(Directives name, DirectiveArgument<?> argument) {
        this.name = name;
        this.argument = argument;
    }

    public static Directive parse(String directive) { // Tested
        String[] parts = directive.split("=");

        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        if (parts.length < 2) {
            return new Directive(fromString(parts[0]), null);
        } else {
            return new Directive(fromString(parts[0]), DirectiveArgument.parse(parts[1]));
        }
    }

    public Directives getName() {
        return name;
    }

    public Optional<DirectiveArgument<?>> getArgument() {
        return Optional.ofNullable(argument);
    }

    public void setArgument(DirectiveArgument<?> argument) {
        this.argument = argument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        if (getClass() != o.getClass())
            return false;
        Directive dir = (Directive) o;

        return this.name.equals(dir.getName())
                && (
                this.argument == null
                        ? dir.getArgument().isEmpty()
                        : this.argument.equals(dir.getArgument().get()));

    }
}
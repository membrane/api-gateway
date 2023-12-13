package com.predic8.membrane.core.interceptor.httpcache;

import com.predic8.membrane.core.http.Header;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toCollection;

public class CacheControlHeader {
    private final LinkedList<Directive> directives;

    private CacheControlHeader(LinkedList<Directive> directives) {
        this.directives = directives;
    }

    // TODO Move to util class
    public static LinkedList<String> httpElementToList(String element) { // Tested
        LinkedList<String> directives = new LinkedList<>();

        String regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";
        String[] splits = element.split(regex);

        for (String split : splits) {
            directives.add(split.trim());
        }

        return directives;
    }

    static LinkedList<Directive> parseDirectives(String directives) {
        return httpElementToList(directives).stream()
                .map(Directive::parse)
                .collect(toCollection(LinkedList::new));
    }

    public static CacheControlHeader parseHeader(Header header) {
        return new CacheControlHeader(parseDirectives(header.getCacheControl()));
    }

    <R> R onDirective(Directives directive, Function<Directive, R> function) {
        for (Directive dir : directives) {
            if (dir.getName().equals(directive)) {
                return function.apply(dir);
            }
        }
        return null;
    }

    public boolean hasDirective(Directives dir) {
        return onDirective(dir, d -> true) != null;
    }

    public boolean directiveHasArgument(Directives dir) {
        return onDirective(dir, d -> d.getArgument().isPresent());
    }

    public Optional<DirectiveArgument<?>> getDirectiveArgument(Directives dir) {
        if (hasDirective(dir) && directiveHasArgument(dir)) {
            return onDirective(dir, Directive::getArgument);
        }
        return Optional.empty();
    }
}

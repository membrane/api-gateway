package com.predic8.membrane.core.interceptor.httpcache;

import com.predic8.membrane.core.http.Header;
import java.util.LinkedList;
import java.util.Map;

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

    static Map<Directives, Directive> parseDirectives(String directives) {
        // TODO | Uses parseHttpElement
        // TODO | Uses Directive.parse()
        return null;
    }

    public static CacheControlHeader parseRaw(String header) {
        //CacheControlHeader cch = new CacheControlHeader();
        // TODO Parse header string | Uses ParseHeader
        return null;
    }

    public static CacheControlHeader parseHeader(Header header) {
        //CacheControlHeader cch = new CacheControlHeader();
        // TODO Parse Header object | Uses
        return null;
    }

    // TODO
    public boolean hasDirective(Directives directive) {
        return true;
    }

    //TODO
    //public Optional<DirectiveArgument<?>> getDirectiveArgument(Directives directiveName) {
    //     return directives.containsKey(directiveName) ? directives.get(directiveName).getArgument() : Optional.empty();
    //}
}

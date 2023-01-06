package com.predic8.membrane.core.graphql;

public class GraphQLUtil {
    public static String insertErrorMarker(String schemaDoc, int position) {
        if (position <= 0)
            return "^" + schemaDoc;
        if (position >= schemaDoc.length())
            return schemaDoc + "^";
        return schemaDoc.substring(0, position) + "^" + schemaDoc.substring(position, schemaDoc.length());
    }

}

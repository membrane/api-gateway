package com.predic8.membrane.core.graphql;

import com.predic8.membrane.core.graphql.model.ExecutableDocument;
import com.predic8.membrane.core.graphql.model.TypeSystemDocument;

import java.io.IOException;
import java.io.InputStream;

public class GraphQLParser {

    TypeSystemDocument parseSchema(InputStream is) throws IOException, ParsingException {
        TypeSystemDocument typeSystemDocument = new TypeSystemDocument();
        typeSystemDocument.parse(new Tokenizer(is));
        return typeSystemDocument;
    }

    ExecutableDocument parseRequest(InputStream is) throws IOException, ParsingException {
        ExecutableDocument executableDocument = new ExecutableDocument();
        executableDocument.parse(new Tokenizer(is));
        return executableDocument;
    }

}

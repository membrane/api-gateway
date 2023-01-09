/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.NAME;

public abstract class DirectiveLocation {
    public static DirectiveLocation parseDirectiveLocation(Tokenizer tokenizer) throws ParsingException {
        if (tokenizer.type() != NAME)
            throw new ParsingException("Expected name.", tokenizer.position());
        String s = tokenizer.string();
        if (ExecutableDirectiveLocation.is(s))
            return new ExecutableDirectiveLocation(s);
        if (TypeSystemDirectiveLocation.is(s))
            return new TypeSystemDirectiveLocation(s);
        throw new ParsingException("Not a valid DirectiveLocation.", tokenizer.position());
    }
}

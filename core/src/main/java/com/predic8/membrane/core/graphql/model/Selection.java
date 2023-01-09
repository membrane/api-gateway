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

import java.io.IOException;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.NAME;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public abstract class Selection {
    public abstract void parse(Tokenizer tokenizer) throws IOException, ParsingException;

    public static Selection parseSelection(Tokenizer tokenizer) throws IOException, ParsingException {
        Selection sel = null;
        if (tokenizer.type() == NAME) {
            sel = new Field();
        } else if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '.') {
            tokenizer.mustAdvance();
            if (tokenizer.type() == NAME && !tokenizer.string().equals("on"))
                sel = new FragmentSpread();
            else
                sel = new InlineFragment();
        }
        if (sel == null)
            throw new ParsingException("Expected field (name) or fragment ('...').", tokenizer.position());
        sel.parse(tokenizer);
        return sel;
    }
}

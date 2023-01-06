package com.predic8.membrane.core.graphql;

import com.predic8.membrane.core.graphql.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.*;

public class ParserUtil {
    public static String parseName(Tokenizer t) throws IOException, ParsingException {
        t.mustAdvance();
        if (t.type() != NAME)
            throw new ParsingException("Expected name.", t.position());
        return t.string();
    }

    public static List<InputValueDefinition> parseOptionalArgumentsDefinition(Tokenizer t) throws IOException, ParsingException {
        if (t.type() != PUNCTUATOR || t.punctuator() != '(')
            return null;
        List<InputValueDefinition> res = new ArrayList<>();
        t.mustAdvance();
        while (t.type() != PUNCTUATOR || t.punctuator() != ')') {
            InputValueDefinition ivd = new InputValueDefinition();
            ivd.parse(t);
            res.add(ivd);

            t.mustAdvance();
        }
        return res;
    }

    public static List<Argument> parseOptionalArguments(Tokenizer t) throws IOException, ParsingException {
        if (t.type() != PUNCTUATOR || t.punctuator() != '(')
            return null;
        List<Argument> res = new ArrayList<>();
        t.mustAdvance();
        while (t.type() != PUNCTUATOR || t.punctuator() != ')') {
            Argument a = new Argument();
            a.parse(t);
            res.add(a);

            t.mustAdvance();
        }
        return res;
    }

    public static List<Directive> parseDirectivesConstOpt(Tokenizer t) throws IOException, ParsingException {
        if (t.type() != PUNCTUATOR || t.punctuator() != '@')
            return null;
        List<Directive> res = new ArrayList<>();
        while (t.type() == PUNCTUATOR && t.punctuator() == '@') {
            Directive d = new Directive();
            d.parse(t);
            res.add(d);

            if (!t.advance())
                break;
        }
        t.revert();
        return res;
    }

    public static Type parseType(Tokenizer tokenizer) throws IOException, ParsingException {
        Type res;
        if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '[') {
            res = new ListType();
            res.parse(tokenizer);
        } else if (tokenizer.type() == NAME) {
            res = new NamedType();
            res.parse(tokenizer);
        } else {
            throw new ParsingException("Expected type.", tokenizer.position());
        }
        return res;
    }

    public static Value parseValueQuestionConst(Tokenizer tokenizer) throws IOException, ParsingException {
        Value value = null;
        if (tokenizer.type() == STRING_VALUE)
            value = new StringValue();
        if (tokenizer.type() == INT_VALUE)
            value = new IntValue();
        if (tokenizer.type() == FLOAT_VALUE)
            value = new FloatValue();
        if (tokenizer.type() == NAME) {
            String name = tokenizer.string();
            if ("true".equals(name) || "false".equals(name))
                value = new BooleanValue();
            else if ("null".equals(name))
                value = new NullValue();
            else
                value = new EnumValue();
        }
        if (tokenizer.type() == PUNCTUATOR) {
            if (tokenizer.punctuator() == '[')
                value = new ListValue();
            if (tokenizer.punctuator() == '{')
                value = new ObjectValue();
        }
        if (value == null)
            throw new ParsingException("not implemented : " + tokenizer.tokenString(), tokenizer.position()); // TODO
        value.parse(tokenizer);
        return value;
    }

    public static Value parseValueConst(Tokenizer tokenizer) throws IOException, ParsingException {
        return parseValueQuestionConst(tokenizer); // TODO
    }

    public static List<Directive> parseDirectivesOpt(Tokenizer tokenizer) throws ParsingException, IOException {
        return parseDirectivesConstOpt(tokenizer); // TODO
    }

    public static List<FieldDefinition> parseFieldsDefinition(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.mustAdvance();
        List<FieldDefinition> res = new ArrayList<>();
        while(true) {
            if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '}')
                return res;

            FieldDefinition fd = new FieldDefinition();
            fd.parse(tokenizer);
            res.add(fd);

            if (!tokenizer.advance())
                throw new ParsingException("Expected '}'.", tokenizer.position());
        }
    }

    public static List<NamedType> parseImplements(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.mustAdvance();
        if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '&')
            tokenizer.mustAdvance();
        List<NamedType> res = new ArrayList<>();
        while(true) {
            NamedType type = new NamedType();
            type.parse(tokenizer);
            if (type.isNullable())
                throw new ParsingException("Nullable types are not supported in 'implements'.", tokenizer.position());
            res.add(type);

            tokenizer.mustAdvance();
            if (tokenizer.type() != PUNCTUATOR || tokenizer.punctuator() != '&')
                break;
            tokenizer.mustAdvance();
        }
        return res;
    }

    public static List<NamedType> parseUnionMemberTypes(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.mustAdvance();
        if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '|')
            tokenizer.mustAdvance();
        List<NamedType> res = new ArrayList<>();
        while(true) {
            NamedType type = new NamedType();
            type.parse(tokenizer);
            if (type.isNullable())
                throw new ParsingException("Nullable types are not supported in 'union'.", tokenizer.position());
            res.add(type);

            if (!tokenizer.advance())
                break;
            if (tokenizer.type() != PUNCTUATOR || tokenizer.punctuator() != '|')
                break;
            tokenizer.mustAdvance();
        }
        return res;
    }

    public static List<Selection> parseSelectionSetOpt(Tokenizer t) throws IOException, ParsingException {
        if (t.type() != PUNCTUATOR || t.punctuator() != '{')
            return null;
        List<Selection> res = new ArrayList<>();
        t.mustAdvance();
        while (t.type() != PUNCTUATOR || t.punctuator() != '}') {
            Selection a = Selection.parseSelection(t);
            res.add(a);

            t.mustAdvance();
        }
        return res;
    }

}


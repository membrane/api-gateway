package com.predic8.membrane.core.lang.spel.typeconverters;

import com.predic8.membrane.core.lang.spel.spelable.*;
import org.springframework.core.convert.converter.*;

public class SpELMapToStringTypeConverter implements Converter<SpELMap, String> {

    @Override
    public String convert(SpELMap m) {
        return m.getValue().toString();
    }
}

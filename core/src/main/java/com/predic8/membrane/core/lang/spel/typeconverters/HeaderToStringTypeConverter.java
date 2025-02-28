package com.predic8.membrane.core.lang.spel.typeconverters;

import com.predic8.membrane.core.lang.spel.spelable.*;
import org.springframework.core.convert.converter.*;

public class HeaderToStringTypeConverter implements Converter<SpELHeader, String> {

    @Override
    public String convert(SpELHeader header) {
        return header.getValue().toString();
    }
}

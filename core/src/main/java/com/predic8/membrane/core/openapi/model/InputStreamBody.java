package com.predic8.membrane.core.openapi.model;

import com.predic8.membrane.core.openapi.model.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.util.Utils.inputStreamToString;

public class InputStreamBody implements Body {

    private InputStream is;

    public InputStreamBody(InputStream is) {
        this.is = is;
    }

    public InputStream getInputStream() {
        return is;
    }

    @Override
    public String asString() throws IOException {
        return inputStreamToString(is);
    }


}

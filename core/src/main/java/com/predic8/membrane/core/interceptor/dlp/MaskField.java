package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class MaskField implements FieldActionStrategy {

    private static final Logger log = LoggerFactory.getLogger(MaskField.class);

    @Override
    public void apply(Message msg, Pattern pattern) {
        try {
            JsonNode root = new ObjectMapper().readTree(msg.getBodyAsStringDecoded());
            JsonUtils.mask(root, pattern);
            byte[] output = new ObjectMapper().writeValueAsBytes(root);
            msg.setBodyContent(output);
            msg.getHeader().setContentLength(output.length);
            msg.getHeader().setContentType("application/json; charset=UTF-8");
        } catch (Exception e) {
            log.error("Masking failed", e);
        }
    }
}


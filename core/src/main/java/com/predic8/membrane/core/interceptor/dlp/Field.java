package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@MCElement(name = "field")
public class Field {


    private static final Logger log = LoggerFactory.getLogger(Field.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String name;
    private Action action = Action.REPORT;
    private Pattern compiled = Pattern.compile(".*");

    @MCAttribute
    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "field name must not be null");
        this.compiled = Pattern.compile(name, Pattern.CASE_INSENSITIVE);
    }

    @MCAttribute
    public void setAction(String action) {
        this.action = Action.valueOf(action.toUpperCase(Locale.ROOT));
    }

    public String getName() {
        return name;
    }

    public String getAction() {
        return action.name().toLowerCase(Locale.ROOT);
    }

    public void handleAction(Message msg) {
        try {
            JsonNode root = MAPPER.readTree(msg.getBodyAsStringDecoded());

            switch (action) {
                case FILTER -> JsonUtils.removeKeysMatching(root, compiled);
                case MASK -> JsonUtils.maskKeysMatching(root, compiled);
                case REPORT -> {/* no?op */}
            }

            byte[] out = MAPPER.writeValueAsBytes(root);
            msg.setBodyContent(out);
            msg.getHeader().setContentLength(out.length);
            msg.getHeader().setContentType("application/json; charset=UTF-8");
        } catch (Exception e) {
            log.error("DLP field action '{}' failed: {}", name, e.toString(), e);
        }
    }

    private enum Action {MASK, FILTER, REPORT}
}

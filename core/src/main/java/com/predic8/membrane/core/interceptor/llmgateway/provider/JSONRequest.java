package com.predic8.membrane.core.interceptor.llmgateway.provider;

import com.fasterxml.jackson.databind.node.ObjectNode;

interface JSONMessage {

    ObjectNode getJson();
}

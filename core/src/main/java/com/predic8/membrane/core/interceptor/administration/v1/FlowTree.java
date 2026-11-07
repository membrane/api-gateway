/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.administration.v1;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.flow.choice.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;

/**
 * Renders a flow as a tree of {@link FlowNode}s, descending into {@code request}, {@code response}, {@code abort},
 * {@code if}, {@code choose} and other plugins that contain a nested flow.
 */
final class FlowTree {

    static final String DOC_BASE_URL = "https://www.membrane-api.io/docs/current/";

    record FlowNode(String name, String displayName, String shortDescription, String longDescription, String docUrl,
                    String flow, String condition, List<FlowNode> children) {
    }

    private FlowTree() {
    }

    static List<FlowNode> of(List<Interceptor> flow) {
        return of(flow, null);
    }

    private static List<FlowNode> of(List<Interceptor> flow, String limitedTo) {
        if (flow == null)
            return List.of();
        final var nodes = new ArrayList<FlowNode>();
        for (final var interceptor : flow)
            nodes.add(node(interceptor, limitedTo));
        return List.copyOf(nodes);
    }

    private static FlowNode node(Interceptor interceptor, String limitedTo) {
        return switch (interceptor) {
            case RequestInterceptor ri -> container(ri, "request", of(ri.getFlow(), "request"));
            case ResponseInterceptor ri -> container(ri, "response", of(ri.getFlow(), "response"));
            case AbortInterceptor ai -> container(ai, "abort", of(ai.getFlow(), "abort"));
            case IfInterceptor ii -> withCondition(ii, limitedTo, ii.getTest(), ifChildren(ii, limitedTo));
            case ChooseInterceptor ci -> withCondition(ci, limitedTo, null, chooseChildren(ci, limitedTo));
            case AbstractFlowInterceptor fi -> withCondition(fi, limitedTo, null, of(fi.getFlow(), limitedTo));
            default -> withCondition(interceptor, limitedTo, null, null);
        };
    }

    private static List<FlowNode> ifChildren(IfInterceptor ii, String limitedTo) {
        final var children = new ArrayList<>(of(ii.getFlow(), limitedTo));
        final var elseFlow = ii.getElseInterceptor();
        if (elseFlow != null && !elseFlow.isEmpty())
            children.add(new FlowNode("else", "else", null, null, null, limitedTo, null, of(elseFlow, limitedTo)));
        return List.copyOf(children);
    }

    private static List<FlowNode> chooseChildren(ChooseInterceptor ci, String limitedTo) {
        final var children = new ArrayList<FlowNode>();
        for (final var choice : ci.getChoices()) {
            if (choice instanceof Case c)
                children.add(new FlowNode("case", "case", null, null, null, limitedTo, c.getTest(), of(c.getFlow(), limitedTo)));
            else
                children.add(new FlowNode("otherwise", "otherwise", null, null, null, limitedTo, null, of(choice.getFlow(), limitedTo)));
        }
        return List.copyOf(children);
    }

    private static FlowNode container(Interceptor interceptor, String flow, List<FlowNode> children) {
        return new FlowNode(elementName(interceptor), interceptor.getDisplayName(), null, null,
                docUrl(interceptor), flow, null, children);
    }

    private static FlowNode withCondition(Interceptor interceptor, String limitedTo, String condition, List<FlowNode> children) {
        return new FlowNode(elementName(interceptor), interceptor.getDisplayName(),
                interceptor.getShortDescription(), interceptor.getLongDescription(), docUrl(interceptor),
                limitedTo != null ? limitedTo : appliedFlow(interceptor.getAppliedFlow()), condition, children);
    }

    static String appliedFlow(EnumSet<Flow> flow) {
        if (flow == null || flow.isEmpty())
            return null;
        if (flow.contains(REQUEST) && flow.contains(RESPONSE))
            return "both";
        if (flow.contains(REQUEST))
            return "request";
        if (flow.contains(RESPONSE))
            return "response";
        return "abort";
    }

    private static String docUrl(Interceptor interceptor) {
        final var element = mcElement(interceptor.getClass());
        return element == null ? null : DOC_BASE_URL + element.name() + ".html";
    }

    static String elementName(Object o) {
        final var element = mcElement(o.getClass());
        return element == null ? o.getClass().getSimpleName() : element.name();
    }

    private static MCElement mcElement(Class<?> clazz) {
        for (var c = clazz; c != null; c = c.getSuperclass()) {
            final var element = c.getAnnotation(MCElement.class);
            if (element != null)
                return element;
        }
        return null;
    }
}

package com.predic8.membrane.annot.beanregistry;

import java.util.ArrayDeque;
import java.util.Deque;

public final class BeanDefinitionContext {
    private static final ThreadLocal<Deque<BeanDefinition>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private BeanDefinitionContext() {
    }

    public static void push(BeanDefinition beanDefinition) {
        STACK.get().push(beanDefinition);
    }

    public static void pop() {
        Deque<BeanDefinition> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    public static BeanDefinition current() {
        Deque<BeanDefinition> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }
}

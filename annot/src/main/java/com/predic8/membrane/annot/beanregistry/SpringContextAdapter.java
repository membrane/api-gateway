package com.predic8.membrane.annot.beanregistry;

import com.predic8.membrane.annot.Grammar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractRefreshableApplicationContext;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adapter between Membrane's BeanRegistry and Spring's ApplicationContext.
 *
 * Methods are only implemented on a need-to-use basis.
 */
public class SpringContextAdapter implements BeanRegistry {

    private final AbstractRefreshableApplicationContext ac;

    public SpringContextAdapter(AbstractRefreshableApplicationContext ac) {
        this.ac = ac;
    }

    @Override
    public Object resolve(String url) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Object> getBeans() {
        return List.of(ac.getBeanDefinitionNames()).stream().map(ac::getBean).toList();
    }

    @Override
    public Grammar getGrammar() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<T> getBeans(Class<T> clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Optional<T> getBean(Class<T> clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void register(String beanName, Object bean) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T registerIfAbsent(Class<T> type, Supplier<T> supplier) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        ac.close();
    }

    public ApplicationContext getApplicationContext() {
        return ac;
    }
}

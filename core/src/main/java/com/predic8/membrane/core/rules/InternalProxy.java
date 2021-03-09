package com.predic8.membrane.core.rules;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

@MCElement(name="internalProxy")
public class InternalProxy extends AbstractProxy{
    private AbstractServiceProxy.Target target;

    public InternalProxy() {
        key = new AbstractRuleKey(-1,null){

        };
    }

    @Override
    protected AbstractProxy getNewInstance() {
        return new InternalProxy();
    }

    @MCChildElement(order=150)
    public void setTarget(AbstractServiceProxy.Target target) {
        this.target = target;
    }

    public AbstractServiceProxy.Target getTarget() {
        return target;
    }
}

package com.predic8.membrane.core.interceptor.ratelimitdraft.quotas;

import com.predic8.membrane.annot.MCAttribute;

public abstract class Quota {

    private int quota;

    public int getQuota() {
        return quota;
    }

    @MCAttribute
    public void setQuota(int quota) {
        this.quota = quota;
    }
}

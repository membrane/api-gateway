package com.predic8.membrane.core.interceptor.ratelimitdraft;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ratelimitdraft.quotas.Quota;

import java.util.Optional;

@MCElement(name = "rateLimit")
public class RateLimitPolicy {

    private int informationThreshold = 0;
    private Quota quota;
    private Partition partition;

    public Optional<String> getPartitionKey(Exchange exc) {
        return Optional.empty();
    }

    public int getInformationThreshold() {
        return informationThreshold;
    }

    @MCAttribute
    public void setInformationThreshold(int informationThreshold) {
        this.informationThreshold = informationThreshold;
    }

    public Quota getQuota() {
        return quota;
    }

    @MCChildElement(order = 1)
    public void setQuota(Quota quota) {
        this.quota = quota;
    }

    public Partition getPartition() {
        return partition;
    }

    @MCChildElement
    public void setPartition(Partition partition) {
        this.partition = partition;
    }
}

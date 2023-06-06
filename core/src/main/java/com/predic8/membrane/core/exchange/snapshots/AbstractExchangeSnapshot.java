/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchange.snapshots;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.http.BodyCollectingMessageObserver;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.util.functionalInterfaces.ExceptionThrowingConsumer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AbstractExchangeSnapshot {

    @JsonIgnore
    private final ExceptionThrowingConsumer<AbstractExchangeSnapshot> bodyCopiedCallback;
    @JsonIgnore
    private final BodyCollectingMessageObserver.Strategy strategy;
    @JsonIgnore
    private final long limit;

    RequestSnapshot request;
    ResponseSnapshot response;
    String originalRequestUri;
    Calendar time;
    String errorMessage;
    ExchangeState status;
    long timeReqSent;
    long timeReqReceived;
    long timeResSent;
    long timeResReceived;
    List<String> destinations;
    String remoteAddr;
    String remoteAddrIp;
    FakeRule rule;
    String server;

    long id;

    /**
     * @param exc the exchange to snapshot
     * @param flow what to copy from the exchange besides general properties
     * @param bodyCopiedCallback will be called once the {@code flow}'s body has been filled. if null, the body stream will be read
     *                 into memory immediately.
     * @param strategy how to handle body lengths exceeding the {@code limit}.
     * @param limit maximum length of the body.
     */
    public AbstractExchangeSnapshot(AbstractExchange exc, Interceptor.Flow flow, ExceptionThrowingConsumer<AbstractExchangeSnapshot> bodyCopiedCallback, BodyCollectingMessageObserver.Strategy strategy, long limit) throws IOException {
        this.bodyCopiedCallback = bodyCopiedCallback;
        this.strategy = strategy;
        this.limit = limit;
        updateFrom(exc, flow);
    }

    /**
     * called by JSON deserializer
     */
    public AbstractExchangeSnapshot() {
        bodyCopiedCallback = null;
        strategy = BodyCollectingMessageObserver.Strategy.ERROR;
        limit = -1;
    }

    public <T extends AbstractExchangeSnapshot> T updateFrom(AbstractExchange source, Interceptor.Flow flow) throws IOException {
        switch (flow) {
            case REQUEST:
                if(source.getRequest() != null)
                    setRequest(new RequestSnapshot(source.getRequest(), bodyCopiedCallback, this, strategy, limit));
                break;
            case RESPONSE:
            case ABORT:
                if(source.getResponse() != null)
                    setResponse(new ResponseSnapshot(source.getResponse(), bodyCopiedCallback, this, strategy, limit));
                break;
        }

        setOriginalRequestUri(source.getOriginalRequestUri());
        setTime(source.getTime());
        setErrorMessage(source.getErrorMessage());
        setStatus(source.getStatus());
        setTimeReqSent(source.getTimeReqSent());
        setTimeReqReceived(source.getTimeReqReceived());
        setTimeResSent(source.getTimeResSent());
        setTimeResReceived(source.getTimeResReceived());
        setDestinations(new ArrayList<>(source.getDestinations()));
        setRemoteAddr(source.getRemoteAddr());
        setRemoteAddrIp(source.getRemoteAddrIp());
        setId(source.getId());
        setRule(new FakeRule(source.getRule()));
        setServer(source.getServer());

        return (T)this;
    }

    public AbstractExchange toAbstractExchange(){
        Exchange exc = new Exchange(null);

        if(getRequest() != null)
            exc.setRequest(this.getRequest().toRequest());
        if(getResponse() != null)
            exc.setResponse(this.getResponse().toResponse());

        exc.setOriginalRequestUri(getOriginalRequestUri());
        exc.setTime(getTime());
        exc.setErrorMessage(getErrorMessage());
        exc.setStatus(getStatus());
        exc.setTimeReqSent(getTimeReqSent());
        exc.setTimeReqReceived(getTimeReqReceived());
        exc.setTimeResSent(getTimeResSent());
        exc.setTimeResReceived(getTimeResReceived());
        exc.setDestinations(new ArrayList<>(getDestinations()));
        exc.setRemoteAddr(getRemoteAddr());
        exc.setRemoteAddrIp(getRemoteAddrIp());
        exc.setId(getId());
        exc.setRule(getRule());
        setServer(getServer());

        return exc;
    }

    public RequestSnapshot getRequest() {
        return request;
    }

    public void setRequest(RequestSnapshot request) {
        this.request = request;
    }

    public ResponseSnapshot getResponse() {
        return response;
    }

    public void setResponse(ResponseSnapshot response) {
        this.response = response;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getOriginalRequestUri() {
        return originalRequestUri;
    }

    public void setOriginalRequestUri(String originalRequestUri) {
        this.originalRequestUri = originalRequestUri;
    }

    public Calendar getTime() {
        return time;
    }

    public void setTime(Calendar time) {
        this.time = time;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public ExchangeState getStatus() {
        return status;
    }

    public void setStatus(ExchangeState status) {
        this.status = status;
    }

    public long getTimeReqSent() {
        return timeReqSent;
    }

    public void setTimeReqSent(long timeReqSent) {
        this.timeReqSent = timeReqSent;
    }

    public long getTimeReqReceived() {
        return timeReqReceived;
    }

    public void setTimeReqReceived(long timeReqReceived) {
        this.timeReqReceived = timeReqReceived;
    }

    public long getTimeResSent() {
        return timeResSent;
    }

    public void setTimeResSent(long timeResSent) {
        this.timeResSent = timeResSent;
    }

    public long getTimeResReceived() {
        return timeResReceived;
    }

    public void setTimeResReceived(long timeResReceived) {
        this.timeResReceived = timeResReceived;
    }

    public List<String> getDestinations() {
        return destinations;
    }

    public void setDestinations(List<String> destinations) {
        this.destinations = destinations;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public String getRemoteAddrIp() {
        return remoteAddrIp;
    }

    public void setRemoteAddrIp(String remoteAddrIp) {
        this.remoteAddrIp = remoteAddrIp;
    }

    public FakeRule getRule() {
        return rule;
    }

    public void setRule(FakeRule rule) {
        this.rule = rule;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }
}

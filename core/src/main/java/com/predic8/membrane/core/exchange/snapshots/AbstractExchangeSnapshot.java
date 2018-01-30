package com.predic8.membrane.core.exchange.snapshots;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.AbstractProxy;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.rules.StatisticCollector;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

import javax.naming.OperationNotSupportedException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AbstractExchangeSnapshot {

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

    public AbstractExchangeSnapshot(AbstractExchange exc) {
        updateFrom(exc);
    }

    public AbstractExchangeSnapshot() {
    }

    public <T extends AbstractExchangeSnapshot> T updateFrom(AbstractExchange source){
        if(source.getRequest() != null)
            setRequest(new RequestSnapshot(source.getRequest()));
        if(source.getResponse() != null)
            setResponse(new ResponseSnapshot(source.getResponse()));

        setOriginalRequestUri(source.getOriginalRequestUri());
        setTime(source.getTime());
        setErrorMessage(source.getErrorMessage());
        setStatus(source.getStatus());
        setTimeReqSent(source.getTimeReqSent());
        setTimeReqReceived(source.getTimeReqReceived());
        setTimeResSent(source.getTimeResSent());
        setTimeResReceived(source.getTimeResReceived());
        setDestinations(source.getDestinations().stream().collect(Collectors.toList()));
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
        exc.setDestinations(getDestinations().stream().collect(Collectors.toList()));
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

package com.predic8.membrane.core.exchange.snapshots;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.ExchangeState;

import javax.naming.OperationNotSupportedException;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

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

        return (T)this;
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
}

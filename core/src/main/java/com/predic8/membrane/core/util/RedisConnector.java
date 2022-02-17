package com.predic8.membrane.core.util;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.springframework.beans.factory.InitializingBean;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.GetExParams;

@MCElement(name = "redis", topLevel = true)
public class RedisConnector  implements InitializingBean {
    private JedisPool pool;
    private String host = "localhost";
    private int port = 6379;
    private int dbNumber = 0;
    private int maxBodySize = 100000;
    //timeout is in seconds
    private int timeout = 600;
    private boolean ssl = false;
    private String prefix;
    private String user;
    private String password;
    private GetExParams params;

    @Override
    public void afterPropertiesSet() throws Exception {
        if(user == null && password != null){
            pool = new JedisPool(new JedisPoolConfig(), host, port, timeout, password, ssl);
        }
        else if(user != null && password != null){
            pool = new JedisPool(new JedisPoolConfig(), host, port, timeout, user, password, ssl);
        }
        else{
            pool = new JedisPool(new JedisPoolConfig(), host, port, ssl);
        }

        params = new GetExParams().ex(getTimeout());
    }


    public Jedis getJedisWithDb(){
        Jedis jedis = pool.getResource();
        jedis.select(dbNumber);
        return jedis;
    }


    public JedisPool getPool() {
        return pool;
    }


    public void setPool(JedisPool pool) {
        this.pool = pool;
    }

    public String getHost() {
        return host;
    }
    @MCAttribute
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    @MCAttribute
    public void setPort(int port) {
        this.port = port;
    }

    public int getDbNumber() {
        return dbNumber;
    }

    @MCAttribute
    public void setDbNumber(int dbNumber) {
        this.dbNumber = dbNumber;
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    @MCAttribute
    public void setMaxBodySize(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public int getTimeout() {
        return timeout;
    }

    @MCAttribute
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public boolean isSsl() {
        return ssl;
    }

    @MCAttribute
    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public String getPrefix() {
        return prefix;
    }

    @MCAttribute
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getUser() {
        return user;
    }

    @MCAttribute
    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    @MCAttribute
    public void setPassword(String password) {
        this.password = password;
    }

    public GetExParams getParams() {
        return params;
    }

    public void setParams(GetExParams params) {
        this.params = params;
    }
}

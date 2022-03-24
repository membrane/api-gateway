/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.util;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.InitializingBean;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.GetExParams;

@MCElement(name = "redis", topLevel = true)
public class RedisConnector  implements InitializingBean {
    private JedisPool pool;
    private String host = "localhost";
    private int port = 6379;
    private int dbNumber = 0;
    //https://partners-intl.aliyun.com/help/en/doc-detail/98726.htm
    //connection size
    private int connectionNumber = 20;
    private int minIdleConnection = 10;
    //timeout is in seconds
    private int timeout = 600;
    private boolean ssl = false;
    private String user;
    private String password;
    private GetExParams params;


    @Override
    public void afterPropertiesSet() throws Exception {
        GenericObjectPoolConfig jedisPoolConfig = new GenericObjectPoolConfig();
        jedisPoolConfig.setMaxTotal(connectionNumber);
        jedisPoolConfig.setMaxIdle(connectionNumber);
        jedisPoolConfig.setMinIdle(minIdleConnection);

        if(user == null && password != null){
            pool = new JedisPool(jedisPoolConfig, host, port, timeout, password, ssl);
        }
        else if(user != null && password != null){
            pool = new JedisPool(jedisPoolConfig, host, port, timeout, user, password, ssl);
        }
        else{
            pool = new JedisPool(jedisPoolConfig, host, port, ssl);
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

    public int getConnectionNumber() {
        return connectionNumber;
    }

    @MCAttribute
    public void setConnectionNumber(int connectionNumber) {
        this.connectionNumber = connectionNumber;
    }

    public int getMinIdleConnection() {
        return minIdleConnection;
    }

    @MCAttribute
    public void setMinIdleConnection(int minIdleConnection) {
        this.minIdleConnection = minIdleConnection;
    }

    public GetExParams getParams() {
        return params;
    }

    public void setParams(GetExParams params) {
        this.params = params;
    }

    public int getDbNumber() {
        return dbNumber;
    }

    /**
     * @description Index number to connect to in Redis. Default is 0
     */
    @MCAttribute
    public void setDbNumber(int dbNumber) {
        this.dbNumber = dbNumber;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * @description Timeout value when connecting to Redis instance. Default is 10000 milliseconds. Unit is in milliseconds
     */
    @MCAttribute
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getUser() {
        return user;
    }

    /**
     * @description Username to use when connecting redis.
     * */
    @MCAttribute
    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    /**
     * @description Password to use when connecting redis.
     * */
    @MCAttribute
    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSsl() {
        return ssl;
    }

    /**
     * @description Used for enabling ssl. Default value is false
     * */
    @MCAttribute
    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }


    public String getHost() {
        return host;
    }

    /**
     * @description Host name to connect to
     * */
    @MCAttribute
    public void setHost(String host) {
        this.host = host;
    }


    public int getPort() {
        return port;
    }

    /**
     * @description Port number to connect to
     * */
    @MCAttribute
    public void setPort(int port) {
        this.port = port;
    }
}

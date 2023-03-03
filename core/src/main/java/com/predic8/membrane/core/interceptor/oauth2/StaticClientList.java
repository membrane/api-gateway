/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

@MCElement(name="staticClientList")
public class StaticClientList implements ClientList {

    private HashMap<String,Client> clientIdsToClients = new HashMap<>();
    private List<Client> clients = new ArrayList<>();

    @Override
    public void init(Router router) {
        setClients(clients); // fix because the setter is called with empty List<Client>
    }

    @Override
    public Client getClient(String clientId)  {
        if(!clientIdsToClients.containsKey(clientId))
            throw new NoSuchElementException();
        return clientIdsToClients.get(clientId);
    }

    public List<Client> getClients(){
        return clients;
    }

    @MCChildElement
    public void setClients(List<Client> clients){
        this.clients = clients;
        clientIdsToClients.clear();
        for(Client c : clients){
            clientIdsToClients.put(c.getClientId(),c);
        }
    }

    public String toString(){
        StringBuilder builder = new StringBuilder();
        for(Client c : clientIdsToClients.values()){
            builder.append(c.getClientId()).append("\n");
        }
        return builder.toString();
    }
}

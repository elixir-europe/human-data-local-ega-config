/*
 * Copyright 2014 EMBL-EBI.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.embl.ebi.ega.configlogservice.endpoints;

import io.netty.handler.codec.http.FullHttpRequest;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.SEE_OTHER;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import uk.ac.embl.ebi.ega.configlogservice.EgaSecureConfigLogService;
import uk.ac.embl.ebi.ega.configlogservice.utils.DatabaseExecutor;
import uk.ac.embl.ebi.ega.configlogservice.utils.MyLoadBalanceClient;
import uk.ac.embl.ebi.ega.configlogservice.utils.MyServerEntry;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

public class ServerService extends ServiceTemplate implements Service {
    
    private int index; // previously used index, for round-robin (future: load-balance)

    public ServerService() {
        this.index = 0;
    }
    
    @Override
    public JSONObject handle(ArrayList<String> id_, Map<String, String> parameters, FullHttpRequest request, DatabaseExecutor dbe) {
        JSONObject json = new JSONObject(); // Start out with common JSON Object

        String id = id_.get(0);
        try {
            if (id.equalsIgnoreCase("types")) { // Available types = keys of the cache hash table
                String[] result = null;
                Set<String> serverKeys = EgaSecureConfigLogService.getServerKeys();
                result = new String[serverKeys.size()];
                
                Iterator<String> iter = serverKeys.iterator();
                for (int i=0; i<serverKeys.size(); i++)
                    result[i] = iter.next();
                
                json.put("header", responseHeader(OK)); // Header Section of the response
                json.put("response", responseSection(result));            
                
            } else { // Cached list of available servers for the requested service
                //String[] result = null;
                Map<String,String>  result = null;
                
                ArrayList<MyServerEntry> servers_element = EgaSecureConfigLogService.getServersEntry(id.toLowerCase());

                // Two load balancing strategies: actual loads, or basic raound-robin
                //if (id.equalsIgnoreCase("res")) { // rotate the index - special case for RES service
                //    if ( (System.currentTimeMillis()-EgaSecureConfigLogService.res_delta) >= 86400000  ) { // 24h
                //        EgaSecureConfigLogService.res_index = (EgaSecureConfigLogService.res_index+1) % servers_element.size();
                //    }
                //    this.index = EgaSecureConfigLogService.res_index;
                //} else
                    this.index = balancedIndex(servers_element); // pick lowest-used server
                                
                //result = (this.index>=0 && this.index<servers_element.size())?new String[]{servers_element.get(this.index)}:new String[]{"No server available!"};
                if (this.index>=0 && this.index<servers_element.size()) {
                    result = servers_element.get(this.index).getMap();
                }
                json.put("header", responseHeader(OK)); // Header Section of the response
                json.put("response", responseSection(result));
                
            }
        } catch (JSONException ex) {
            Logger.getLogger(StatService.class.getName()).log(Level.SEVERE, null, ex);
            try {
                json.put("header", responseHeader(SEE_OTHER, ex.getLocalizedMessage()));
                json.put("response", responseSection(new String[]{}));            
            } catch (JSONException ex1) {
                Logger.getLogger(StatService.class.getName()).log(Level.SEVERE, null, ex1);
            }
        }

        return json;
    }
    
    // Contact each of the listed servers (if there are more than 1), get lowest load
    private int balancedIndex(ArrayList<MyServerEntry> servers) {
        if (servers.size() == 1)
            return 0;
        
        // If there is more than 1 server available, get load balanced index
        int idx = 0;
        double[] loads = new double[servers.size()];
        
        int to_test = servers.size();
        MyLoadBalanceClient tester[] = new MyLoadBalanceClient[to_test];
        Thread[] executor = new Thread[to_test];
        
        // Simultaneously request status of all possible servers
        for (int i = 0; i < servers.size(); i++) {
            String url = servers.get(i).getServerURL();
            tester[i] = new MyLoadBalanceClient(url, false);
            executor[i] = new Thread(tester[i]);
            executor[i].start();
        }
        
        // ping all servers, pick 'best' (first 'good') one
        for (int i=0; i<to_test; i++) {
            while (executor[i].isAlive()) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ex) {;}
            }
            loads[i] = tester[i].getLoad();
            if (loads[i] > 0 && loads[i] <= 10) {
                index = i;
                return i; // pick first server <= 10 CPU load
            }
        }

        // Otherwise, if no server is below 10 - pick least busy
        double load_min = 101.0; // sentinel start value
        for (int i = 0; i < servers.size(); i++) {
            if (loads[i] < load_min && loads[i] >= 0) {
                load_min = loads[i];
                idx = i;
            }
        }

        if (load_min <= 90 && load_min > -1) {
            index = idx;
            return idx;            
        } else
            return -1; // No server could be found ...
    }
    
}

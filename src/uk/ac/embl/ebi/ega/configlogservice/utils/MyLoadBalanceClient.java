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

package uk.ac.embl.ebi.ega.configlogservice.utils;

import uk.ac.embl.ebi.ega.configlogservice.EgaSecureConfigLogService;
import us.monoid.json.JSONArray;
import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

/**
 *
 * @author asenf
 */
public class MyLoadBalanceClient implements Runnable {
    private final String server;
    private final boolean ssl;    
    private final Resty r;

    private double load;
    
    public MyLoadBalanceClient(String server, boolean ssl) {
        this.server = server;
        this.ssl = ssl;
        
        r = new Resty();
        load = -1.0;
    }
    
    @Override
    public void run() {        
        try {
            String query = server + "/stats/load";
            //String query = ssl?"https://":"http://";
            //query = query + server + "load";
            JSONResource json = r.json(query);
            JSONObject jobj = (JSONObject) json.get("response");
            JSONArray jsonarr = (JSONArray)jobj.get("result");
            for (int i=0; i<jsonarr.length(); i++) {
                String request = jsonarr.getString(i);
                load = Double.parseDouble(request.trim());
            }
        } catch (Exception ex) {
            EgaSecureConfigLogService.log("Error connecting to " + server + " (" + ex.getMessage() + ")");
            //System.out.println("Error connecting to " + server + " (" + ex.getMessage() + ")");
        } 
    }
 
    public double getLoad() {
        return this.load;
    }
    
    public String getServer() {
        return this.server;
    }    
}

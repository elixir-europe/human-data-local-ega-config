package uk.ac.embl.ebi.ega.configlogservice.utils;

/*
 * Copyright 2015 EMBL-EBI.
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


import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 *
 * @author asenf
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class MyServerEntry implements Serializable {
    @XmlElement(name = "protocol")
    private final String Protocol;
    @XmlElement(name = "type")
    private final String type;
    @XmlElement(name = "server")
    private final String Server;
    @XmlElement(name = "port")
    private final String Port;
    @XmlElement(name = "baseUrl")
    private final String BaseUrl;
    @XmlElement(name = "name")
    private final String Name;
    @XmlElement(name = "version")
    private final String Version;
    
    public MyServerEntry(String url) {
        String url_copy = url.trim();
        if (url_copy.endsWith("/"))
            url_copy = url_copy.substring(0, url_copy.length()-1);
        
        String Protocol = url_copy.substring(0, url_copy.indexOf("//"));
        url_copy = url_copy.substring(url_copy.indexOf("//") + 2);
        
        String Server = url_copy.substring(0, url_copy.indexOf(":"));
        url_copy = url_copy.substring(url_copy.indexOf(":") + 1);

        String Port = url_copy.substring(0, url_copy.indexOf("/"));
        url_copy = url_copy.substring(url_copy.indexOf("/") + 1);

        String Version = url_copy.substring(url_copy.lastIndexOf("/") + 1);
        url_copy = url_copy.substring(0, url_copy.lastIndexOf("/"));

        String Name = url_copy.substring(url_copy.lastIndexOf("/") + 1);
        String Type = Name; 
        url_copy = url_copy.substring(0, url_copy.lastIndexOf("/")+1);

        String BaseUrl = url_copy;
                
        this.Protocol = Protocol;
        this.type = Type;
        this.Server = Server;
        this.Port = Port;
        this.BaseUrl = BaseUrl;
        this.Name = Name;
        this.Version = Version;
    }
            
    public MyServerEntry(String Protocol,
                         String Type,
                         String Server,
                         String Port,
                         String BaseUrl,
                         String Name,
                         String Version) {
        this.Protocol = Protocol;
        this.type = Type;
        this.Server = Server;
        this.Port = Port;
        this.BaseUrl = BaseUrl;
        this.Name = Name;
        this.Version = Version;
    }
    
    public String getProtocol() {
        return this.Protocol;
    }
    
    public String getType() {
        return this.type;
    }

    public String getServer() {
        return this.Server;
    }
    
    public String getPort() {
        return this.Port;
    }
    
    public String getBaseUrl() {
        return this.BaseUrl;
    }
    
    public String getName() {
        return this.Name;
    }
    
    public String getVersion() {
        return this.Version;
    }    
    
    public String getServerURL() {
        String the_url = this.Protocol + "//" + this.Server + ":" + this.Port + "/" + this.BaseUrl + this.Name + "/" + this.Version;
        return the_url;
    }
    
    public Map<String,String> getMap() {
        Map<String,String> result = new LinkedHashMap<>();

        result.put("protocol", Protocol);
        result.put("type", type);
        result.put("server", Server);
        result.put("port", Port);
        result.put("baseUrl", BaseUrl);
        result.put("name", Name);
        result.put("version", Version);
                
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("[protocol='%s', name='%s', port='%s', baseUrl='%s', version='%s']", 
                this.Protocol, this.Name, this.Port, this.BaseUrl, this.Version);
    }
}

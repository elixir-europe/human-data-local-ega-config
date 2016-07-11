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
package uk.ac.embl.ebi.ega.configlogservice;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.ini4j.Ini;
import org.ini4j.Profile;
import uk.ac.embl.ebi.ega.configlogservice.endpoints.EntryService;
import uk.ac.embl.ebi.ega.configlogservice.endpoints.EventService;
import uk.ac.embl.ebi.ega.configlogservice.endpoints.ServerService;
import uk.ac.embl.ebi.ega.configlogservice.endpoints.Service;
import uk.ac.embl.ebi.ega.configlogservice.endpoints.StatService;
import uk.ac.embl.ebi.ega.configlogservice.utils.Dailylog;
import uk.ac.embl.ebi.ega.configlogservice.utils.DatabaseExecutor;
import uk.ac.embl.ebi.ega.configlogservice.utils.MyLoadTest;
import uk.ac.embl.ebi.ega.configlogservice.utils.MyServerEntry;
import us.monoid.json.JSONArray;
import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;
import static us.monoid.web.Resty.content;
import static us.monoid.web.Resty.data;
import static us.monoid.web.Resty.form;

public class EgaSecureConfigLogService {
    private static final boolean SSL = false;
    private static int port = 9228;
    public static boolean testMode = false;
    public static boolean noDB = false;
    private static int simultaneous = 500, repeats = 1;
    private static ArrayList<String> fd_list = null;
    private static int myPID = -1;
    
    private static Dailylog dailylog;

    // Database Access Object - for Log
    private static String dbe_path = "";
    private DatabaseExecutor dbe = null;
    // Ini file date - for Config
    private static long file_date = 0L;
    private static String ini_name = "configlog.ini";
    
    // List of Services + Servers
    private static HashMap<String, ArrayList<MyServerEntry>> resources_entry;
    public static volatile int res_index = 0; // start at 0
    public static volatile long res_delta = System.currentTimeMillis();
    
    // Shutdown process: Wait until current operations complete
    static volatile boolean keepRunning = true;
    
    public EgaSecureConfigLogService(int port) {
        EgaSecureConfigLogService.port = port;
        
        this.dbe = null;
        if (!EgaSecureConfigLogService.noDB)
            this.dbe = new DatabaseExecutor(dbe_path + ini_name);        
    }
    
    public void run(HashMap<String, Service> mappings) throws Exception {
        // Configure SSL.
        SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate(); // DEVELOPMENT
            sslCtx = SslContext.newServerContext(SslProvider.JDK, ssc.certificate(), ssc.privateKey());
        } else {
            sslCtx = null;
        }
                        
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(2);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             //.handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new EgaSecureConfigLogServiceInitializer(sslCtx, mappings, this.dbe));

            Channel ch = b.bind(port).sync().channel();

            System.err.println("Open your web browser and navigate to " +
                    (SSL? "https" : "http") + "://127.0.0.1:" + port + '/');

            if (testMode)
                testMe();
        
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
    
    /**
     * @param args the command line arguments
     * 
     * Parameters:
     *      -l path : location of the config file (default: "")
     *      -p port : server port (default 9128)
     *      -t : test me
     */
    public static void main(String[] args) {
        String p = "9228"; int pi = 9228;

        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                keepRunning = false;
//                try {
//                    mainThread.join();
//                } catch (InterruptedException ex) {;}

                System.out.println("Shutdown!!");
            }
        });

        Options options = new Options();

        options.addOption("p", true, "port");        
        options.addOption("l", true, "path");        
        options.addOption("i", true, "inifilename");        
        options.addOption("t", false, "testMe");        
        options.addOption("s", true, "simultaneous");
        options.addOption("r", true, "repeats");
        options.addOption("n", false, "noDB");        
        
        CommandLineParser parser = new BasicParser();
        try {        
            CommandLine cmd = parser.parse( options, args);
            
            if (cmd.hasOption("p"))
                p = cmd.getOptionValue("p");
            if (cmd.hasOption("l"))
                EgaSecureConfigLogService.dbe_path = cmd.getOptionValue("l");
            if (cmd.hasOption("i"))
                EgaSecureConfigLogService.ini_name = cmd.getOptionValue("i");
            if (cmd.hasOption("t")) {
                EgaSecureConfigLogService.testMode = true;
                
                if (cmd.hasOption("s"))
                    EgaSecureConfigLogService.simultaneous = Integer.parseInt(cmd.getOptionValue("s"));
                if (cmd.hasOption("r"))
                    EgaSecureConfigLogService.repeats = Integer.parseInt(cmd.getOptionValue("r"));
                if (cmd.hasOption("n"))
                    EgaSecureConfigLogService.noDB = true;
            }
            // Get Linux Process ID
            java.lang.management.RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
            java.lang.reflect.Field jvm = runtime.getClass().getDeclaredField("jvm");
            jvm.setAccessible(true);
            sun.management.VMManagement mgmt = (sun.management.VMManagement) jvm.get(runtime);
            java.lang.reflect.Method pid_method = mgmt.getClass().getDeclaredMethod("getProcessId");
            pid_method.setAccessible(true);
            int pid = (Integer) pid_method.invoke(mgmt);  
            System.out.println("My PID: " + pid);
            EgaSecureConfigLogService.myPID = pid;
            
            pi = Integer.parseInt(p);
        } catch (ParseException ex) {
            System.out.println("Unrecognized Parameter. Use '-l'  '-i'  -p'  '-t'.");
            Logger.getLogger(EgaSecureConfigLogService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException | NoSuchMethodException | InvocationTargetException ex) {
            Logger.getLogger(EgaSecureConfigLogService.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        if (EgaSecureConfigLogService.testMode)
            printFileHandles();
        
        // Get Resources from ini file
        readResources();
        
        // Add Service Endpoints
        EntryService entryService = new EntryService();
        EventService eventService = new EventService();
        ServerService serverService = new ServerService();
        StatService statService = new StatService();
        
        HashMap<String, Service> mappings = new HashMap<>();
        mappings.put("/entries", entryService);      // POST Log Entries
        mappings.put("/events", eventService);       // POST Log Entries
        mappings.put("/services", serverService);    // GET Server URLs
        mappings.put("/stats", statService);
        
        // Set up Log File
        if (!EgaSecureConfigLogService.testMode)
            EgaSecureConfigLogService.dailylog = new Dailylog("configlog");
        
        // Start and run the server
        try {
            new EgaSecureConfigLogService(pi).run(mappings);
        } catch (Exception ex) {
            Logger.getLogger(EgaSecureConfigLogService.class.getName()).log(Level.SEVERE, null, ex);
        }        
    }
    
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    public static void readResources() {
        // Read Ini File, configure database
        File iniFile = new File(dbe_path + ini_name);
        EgaSecureConfigLogService.file_date = iniFile.lastModified();
        Ini ini = null;
        try {
            ini = new Ini(iniFile);
        } catch (IOException ex) {
            Logger.getLogger(EgaSecureConfigLogService.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // Read section for URL entries
        if (ini != null) {            
            // Resources section (ignore rest) ---------------------------------
            Profile.Section section = ini.get("resources");
            
            // Configure database pool with it
            String data = "";
            if (section.containsKey("data"))
                data = section.get("data");
            String res = "";
            if (section.containsKey("res"))
                res = section.get("res");
            String access = "";
            if (section.containsKey("access"))
                access = section.get("access");
            
            // Clear out old entries
            if (resources_entry==null)
                resources_entry = new HashMap<>();
            else
                resources_entry.clear();

            // Process entries, place in HashMap
            if (data!=null && data.length() > 0) {
                StringTokenizer dataToken = new StringTokenizer(data, ",");
                ArrayList<MyServerEntry> dataEntries = new ArrayList<>();
                while (dataToken.hasMoreTokens()) {
                    String token = dataToken.nextToken();
                    dataEntries.add(new MyServerEntry(token));
                }
                resources_entry.put("data", dataEntries);
            }            
            if (res!=null && res.length() > 0) {
                StringTokenizer resToken = new StringTokenizer(res, ",");
                ArrayList<MyServerEntry> resEntries = new ArrayList<>();
                while (resToken.hasMoreTokens()) {
                    String token = resToken.nextToken();
                    resEntries.add(new MyServerEntry(token));
                }
                resources_entry.put("res", resEntries);
            }
            if (access!=null && access.length() > 0) {
                StringTokenizer accessToken = new StringTokenizer(access, ",");
                ArrayList<MyServerEntry> accessEntries = new ArrayList<>();
                while (accessToken.hasMoreTokens()) {
                    String token = accessToken.nextToken();
                    accessEntries.add(new MyServerEntry(token));
                }
                resources_entry.put("access", accessEntries);
            }            
        }
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    public static ArrayList<MyServerEntry> getServersEntry(String service) {
        return EgaSecureConfigLogService.resources_entry.get(service.toLowerCase());
    }
    
    public static Set<String> getServerKeys() {
        return EgaSecureConfigLogService.resources_entry.keySet();
    }

    public static void updateConfig() {
        File xml = new File(dbe_path + ini_name);
        if (xml.lastModified() > EgaSecureConfigLogService.file_date)
            readResources();
    }

    public static void log(String text) {
        if (EgaSecureConfigLogService.dailylog!=null)
            EgaSecureConfigLogService.dailylog.log(text);
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    public static double getSystemCpuLoad() throws MalformedObjectNameException, ReflectionException, InstanceNotFoundException {

        MBeanServer mbs    = ManagementFactory.getPlatformMBeanServer();
        ObjectName name    = ObjectName.getInstance("java.lang:type=OperatingSystem");
        AttributeList list = mbs.getAttributes(name, new String[]{ "SystemCpuLoad" });

        if (list.isEmpty())     return Double.NaN;

        Attribute att = (Attribute)list.get(0);
        Double value  = (Double)att.getValue();

        if (value == -1.0)      return Double.NaN;  // usually takes a couple of seconds before we get real values

        return ((int)(value * 1000) / 10.0);        // returns a percentage value with 1 decimal point precision
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // Self-Test of functionality provided in this server
    private void testMe() throws Exception {
        // Wait until server has started up
        Thread.sleep(2000);
        System.out.println("Test Started");
        
        EgaSecureConfigLogServiceHandler.load_ceiling = 100.0; // ignore server loads for testing
        Resty r = new Resty();
        
        // Test 1: Get all types of servers (which are then used for the next query)
        String query_saved = "";
        {
            String query = "http://localhost:" + EgaSecureConfigLogService.port + "/ega/rest/configlog/v2/Services/types";
            System.out.println("First query: " + query);
            JSONResource json = r.json(query);
            JSONObject jobj = (JSONObject) json.get("response");
            JSONArray jsonarr = (JSONArray)jobj.get("result");
            System.out.println("Available Server Types: " + jsonarr.length());
            for (int i=0; i<jsonarr.length(); i++) {
                String request = jsonarr.getString(i);
                System.out.println("Type "+i+": " + request);
            }
            Thread.sleep(10);

            // Test 2: Attempt to get one of each type of server
            String query_ = "";
            for (int j=0; j<jsonarr.length(); j++) {
                query = "http://localhost:" + EgaSecureConfigLogService.port + "/ega/rest/configlog/v2/Services/" + jsonarr.getString(j);
                json = r.json(query);
                jobj = (JSONObject) json.get("response");
                JSONArray jsonarr1 = (JSONArray)jobj.get("result");
                if (jsonarr1.length() >=1 ) {
                    JSONObject jobj2 = (JSONObject)jsonarr1.get(0); // There should ever only be 1

                    System.out.println(jsonarr.getString(j) + " Servers: " + jsonarr1.length() + " (should be 1)");
                    query_ = jobj2.getString("protocol") + "://" + 
                                jobj2.getString("server") + ":" + 
                                jobj2.getString("port") + "/" + 
                                jobj2.getString("baseUrl") +
                                jobj2.getString("version") + "/" + 
                                jobj2.getString("name");

                    System.out.println("Server: " + query_);
                    if (query_saved.length() == 0)
                        query_saved = query;
                } else {
                    System.out.println("No Server returned (could be b/c in test mode there is no load balancing");
                }
            }
            Thread.sleep(10);
        }
        
        // Test 3: Query the server load
        {
            String query = "http://localhost:" + EgaSecureConfigLogService.port + "/ega/rest/configlog/v2/Stats/load";
            JSONResource json = r.json(query);
            JSONObject jobj = (JSONObject) json.get("response");
            JSONArray jsonarr = (JSONArray)jobj.get("result");
            System.out.println("Loads (should be 1): " + jsonarr.length());
            for (int i=0; i<jsonarr.length(); i++) {
                String request = jsonarr.getString(i);
                System.out.println("Load "+i+": " + request);
            }
            Thread.sleep(10);
        }

        
        // Test 4: Brief load test - numTests simultaneous requests for one type of resource
        System.out.println("Load test with query: " + query_saved + "; verify that load test works.");
        printFileHandles();
        (new MyLoadTest(query_saved, -1)).run();
        System.out.println("-----");
        for (int ii=0; ii<EgaSecureConfigLogService.repeats; ii++) {
            printFileHandles();
            System.out.println("\n\nIteration: " + ii + "\n\n");
            int numTests = EgaSecureConfigLogService.simultaneous;
            System.out.println("Load test: " + numTests + " simultaneous requests.");
            MyLoadTest[] tests = new MyLoadTest[numTests];
            Thread[] queries = new Thread[numTests];
            for (int i=0; i<numTests; i++) { // Set up all threads
                tests[i] = new MyLoadTest(query_saved, i);
                queries[i] = new Thread(tests[i]);
            }

            for (int i=0; i<numTests; i++) // Run all threads
                queries[i].start();

            long min = 0, max = 0;
            double avg = 0.0;
            for (int i=0; i<numTests; i++) {
                while (queries[i].isAlive()) {
                    Thread.sleep(2);
                }
                long t = tests[i].getDelta();
                if (t > max) max = t;
                if (min==0 || t < min) min = t;
                avg += t;
            }

            System.out.println("Max: " + max);
            System.out.println("Min: " + min);
            System.out.println("Avg: " + (avg/numTests));
        }
/*        
        // Test 4: Brief load test - numTests simultaneous requests for one type of resource
        {
            String query_saved = "http://localhost:9228/ega/rest/configlog/v2/Services/data";
            int numTests = 500;
            System.out.println("Load test: " + numTests + " simultaneous requests.");
            MyLoadTest[] tests = new MyLoadTest[numTests];
            Thread[] queries = new Thread[numTests];
                Resty test_r = new Resty();
            for (int i=0; i<numTests; i++) { // Set up all threads
                tests[i] = new MyLoadTest(query_saved, i, test_r);
                queries[i] = new Thread(tests[i]);
            }

            for (int i=0; i<numTests; i++) // Run all threads
                queries[i].start();

            long min = 0, max = 0;
            double avg = 0.0;
            for (int i=0; i<numTests; i++) {
                while (queries[i].isAlive()) {
                    Thread.sleep(2);
                }
                long t = tests[i].getDelta();
                if (t > max) max = t;
                if (min==0 || t < min) min = t;
                avg += t;
            }

            System.out.println("Max: " + max);
            System.out.println("Min: " + min);
            System.out.println("Avg: " + (avg/numTests));
        }
*/        
        // Test 5: Entry
        {
            System.out.println("Test posting to Download");
            JSONObject json1 = new JSONObject();
            json1.put("user", "-");
            json1.put("ip", "127.0.0.1");
            json1.put("server", "los");
            json1.put("fileid", "--");
            json1.put("dspeed", "---");
            json1.put("dstatus", "success");
            json1.put("dprotocol", "-----");
            json1.put("fileformat", "------");
            JSONResource json = r.json("http://localhost:" + EgaSecureConfigLogService.port + 
                    "/ega/rest/configlog/v2/Entries/entry", form( data("logrequest", content(json1)) ));
            JSONObject jobj = (JSONObject) json.get("response");
            JSONArray jsonarr = (JSONArray)jobj.get("result");
            for (int i=0; i<jsonarr.length(); i++) {
                System.out.println(jsonarr.getString(i));
            }
        }
        
        // Test 2: Event
        {
            System.out.println("Test posting to Event");
            JSONObject json2 = new JSONObject();
            json2.put("user", "test@user");
            json2.put("ip", "127.0.0.1");
            json2.put("event", "Self-Test Event");
            json2.put("ticket", "123-456-768");
            json2.put("session", "ab3-gs2-tr2");
            json2.put("type", "test");
            JSONResource json = r.json("http://localhost:" + EgaSecureConfigLogService.port + 
                    "/ega/rest/configlog/v2/Events/event", form( data("eventrequest", content(json2)) ));
            JSONObject jobj = (JSONObject) json.get("response");
            JSONArray jsonarr = (JSONArray)jobj.get("result");
            for (int i=0; i<jsonarr.length(); i++) {
                System.out.println(jsonarr.getString(i));
            }
        }
        
        System.exit(100); // End the server after self test is complete
        
    }

    private static void printFileHandles() {
        printFileHandles(false);
    }
    private static void printFileHandles(boolean all) {
        InputStream is = null;
        try {
            int c;
            is = Runtime.getRuntime().exec(new String[] {"bash", "-c", "lsof | wc -l"}).getInputStream();
            while ((c = is.read()) != -1)
                System.out.write(c);
            
            if (EgaSecureConfigLogService.fd_list == null) {
                EgaSecureConfigLogService.fd_list = new ArrayList<>();
            }
            if (EgaSecureConfigLogService.myPID == -1)
                is = Runtime.getRuntime().exec(new String[] {"bash", "-c", "lsof -i"}).getInputStream();
            else {
                String command = "lsof -i | grep " + EgaSecureConfigLogService.myPID;
                is = Runtime.getRuntime().exec(new String[] {"bash", "-c", command}).getInputStream();                
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            String line;
            while ( (line = in.readLine()) != null ) {
                boolean print = true;
                for (int i=0; i<EgaSecureConfigLogService.fd_list.size(); i++) {
                    if (EgaSecureConfigLogService.fd_list.get(i).equals(line))
                        print = false;
                }
                if (print) {
                    EgaSecureConfigLogService.fd_list.add(line);
                    System.out.println("Line: " + line);
                }
                if (!print && all)
                    System.out.println("Line (all): " + line);
            }
            
        } catch (IOException ex) {
            ;
        } finally {
            if (is!=null) try {
                is.close();
            } catch (IOException ex) {;}
        }        
    }
}

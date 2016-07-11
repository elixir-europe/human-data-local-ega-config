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

/*
 * This class provides responses to REST URLs
 * This service will run ONLY inside the EGA Vault and is not available anywhere else
 * For this reason it uses plain http and no user account information
 *
 * URL Prefix for his server is: /ega/rest/configlog/v2
 *
 * Resources are:
 *
 * /servers/types                   Get available service types
 * /servers/{type}                  Get server URL for specified service type
 *
 * POST /entries/entry []           Store a download log entry in permanent storage (file, db)
 * POST /events/... []              Store an event log entry in permanent storage (file, db)
 * 
 * /stats/load                      Get CPU load of Server
 * 
 */

package uk.ac.embl.ebi.ega.configlogservice;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.DATE;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaderNames.LAST_MODIFIED;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.TOO_MANY_REQUESTS;
import static io.netty.handler.codec.http.HttpResponseStatus.GONE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.util.CharsetUtil;
import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.activation.MimetypesFileTypeMap;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.unbescape.html.HtmlEscape;
import uk.ac.embl.ebi.ega.configlogservice.endpoints.Service;
import uk.ac.embl.ebi.ega.configlogservice.utils.DatabaseExecutor;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;
import us.monoid.json.XML;

/**
 *
 * This is unique/exclusive for each connection - place user interaction caches here
 */
public class EgaSecureConfigLogServiceHandler extends SimpleChannelInboundHandler<FullHttpRequest> { // (1)

    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;

    public static double load_ceiling = 100.0; // See if this impacts the server; initial indication is that it doesn't
    
    // Handle session unique information
    private boolean SSL = false, active = true;
    private final DatabaseExecutor dbe;
    private final HashMap<String, Service> endpointMappings;

    public EgaSecureConfigLogServiceHandler(boolean SSL, HashMap<String, Service> mappings, DatabaseExecutor dbe) throws NoSuchAlgorithmException {
        super();
        this.SSL = SSL;
        this.endpointMappings = mappings;
        this.dbe = dbe;
    }

    // *************************************************************************
    // *************************************************************************
    @Override
    public void messageReceived(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        long time = System.currentTimeMillis();
        double load = EgaSecureConfigLogService.getSystemCpuLoad();

        String get = request.headers().get("Accept").toString(); // Response Type
        
        // Active?
        if (!EgaSecureConfigLogService.keepRunning) {
            sendError(ctx, GONE, get);
            return;
        }
        // Step 1: If server load too high, reject connection
        if (!active || load > load_ceiling) { // This server doesn't really produce much load
            System.out.println("load=" + load); // DEVELOPMENT
            sendError(ctx, TOO_MANY_REQUESTS, get);
            return;
        }
        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, BAD_REQUEST, get);
            return;
        }        
        if (request.method() != GET && request.method() != POST) {
            sendError(ctx, METHOD_NOT_ALLOWED, get);
            return;
        }

        // Sanitize provided URL based on OWASP guidelines
        PolicyFactory policy = Sanitizers.FORMATTING.and(Sanitizers.LINKS);
        String safeUri = policy.sanitize(request.uri()); // take directly as provided by client, and sanitize it
        String unescapedSafeUri = HtmlEscape.unescapeHtml(safeUri);
        EgaSecureConfigLogService.log(unescapedSafeUri);
        if (EgaSecureConfigLogService.testMode)
            System.out.println("URL: " + unescapedSafeUri);

        // Step 2: Parse URL to determine what action to take; and take action
        String newUrl = unescapedSafeUri.startsWith("http")?unescapedSafeUri:("http://" + unescapedSafeUri);
        URL user_action = new URL(newUrl);
        //URL user_action = new URL("http://" + unescapedSafeUri);
        String path = user_action.getPath();
        Map<String, String> parameters = new LinkedHashMap<>();
        String[] pairs = user_action.getQuery()!=null?user_action.getQuery().split("&"):null;
        if (pairs!=null) for (String pair : pairs) {
            int idx = pair.indexOf("=");
            parameters.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }

        // process the path (1) verify root and service (2) determine function & resource
        String function = "";
        ArrayList<String> id = new ArrayList<>();
        try {
            StringTokenizer token = new StringTokenizer(path, "/");
            String t = token.nextToken();
            assert(t.equalsIgnoreCase("ega"));
            t = token.nextToken();
            assert(t.equalsIgnoreCase("rest"));
            t = token.nextToken();
            assert(t.equalsIgnoreCase("configlog"));
            t = token.nextToken();
            assert(t.equalsIgnoreCase("v2"));
            function = "/" + token.nextToken().toLowerCase();
            while (token.hasMoreTokens()) {
                id.add(token.nextToken());
            }
        } catch (Throwable t) {
            sendError(ctx, BAD_REQUEST, get); // If the URL is incorrect...
            return;
        }

        // Map function to endpoint, process request
        JSONObject json = null;
        if (this.endpointMappings.containsKey(function)) {
            json = this.endpointMappings.get(function).handle(id, parameters, request, dbe);
        } else {
            sendError(ctx, BAD_REQUEST, get); // If the URL Function is incorrect...
            return;
        }
        
        // Step 3: Prepare a response - set content typt to the expected type
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK);
        StringBuilder buf = new StringBuilder();
        //if (get.contains("application/json")) { // Format list of values as JSON
            response.headers().set(CONTENT_TYPE, "application/json");
            buf.append(json.toString());
        //} else {
        //    sendError(ctx, BAD_REQUEST, get);
        //    return;
        //}
        
        // Step 4: Result has been obtained. Build response and send to requestor
        ByteBuf buffer = Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        
        // Cleanup
        buffer.release();
        
        // Total time log (time of message, function, id, delta)
        String entry = String.valueOf(System.currentTimeMillis()-time) + "_" + id;

        // Update server list, if necessary
        EgaSecureConfigLogService.updateConfig();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (ctx.channel().isActive()) {
            sendError(ctx, INTERNAL_SERVER_ERROR);
        }
    }

    //private static void sendListing(ChannelHandlerContext ctx, File dir) {
    private static void sendListing(ChannelHandlerContext ctx, String[] list, 
            String path, String prefix) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK);
        response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        
        if (list.length > 0 && list[0].length() > 0 && !list[0].startsWith("/"))
            prefix = prefix+"/";

        StringBuilder buf = new StringBuilder();
        //String dirPath = dir.getPath();

        buf.append("<!DOCTYPE html>\r\n");
        buf.append("<html><head><title>");
        buf.append("Listing of: ");
        buf.append(path);
        buf.append("</title></head><body>\r\n");

        buf.append("<h3>Listing of: ");
        buf.append(path);
        buf.append("</h3>\r\n");

        buf.append("<ul>");
        buf.append("<li><a href=\"../\">..</a></li>\r\n");

        for (int i=0; i<list.length; i++) {
            String list_url = list[i];
            if (list_url.contains("\t")) list_url = list_url.substring(0,list_url.indexOf("\t"));
        
            buf.append("<li><a href=\"");
            buf.append(prefix + list_url);
            buf.append("\">");
            buf.append(list[i]);
            buf.append("</a></li>\r\n");
        }

        buf.append("</ul></body></html>\r\n");
        ByteBuf buffer = Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        buffer.release();

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    //private static void sendRedirect(ChannelHandlerContext ctx, String newUri) {
    //    FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, FOUND);
    //    response.headers().set(LOCATION, newUri);
    //
    //    // Close the connection as soon as the error message is sent.
    //    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    //}

    // JSON Version of error messages
    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        sendError(ctx, status, "application/json");
    }
    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, String get) {
        EgaSecureConfigLogService.log(status.toString());
        try {
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status);
            JSONObject json = new JSONObject(); // Start out with common JSON Object
            json.put("header", responseHeader(status)); // Header Section of the response
            json.put("response", "null"); // ??
            
            StringBuilder buf = new StringBuilder();
            if (get.contains("application/json")) { // Format list of values as JSON
                response.headers().set(CONTENT_TYPE, "application/json");
                buf.append(json.toString());
            } else if (get.contains("xml")) { // Format list of values as XML
                response.headers().set(CONTENT_TYPE, "application/xml");
                String xml = XML.toString(json);
                buf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                buf.append("<Result>");
                buf.append(xml);
                buf.append("</Result>");
            }
            
            ByteBuf buffer = Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8);
            response.content().writeBytes(buffer);
            
            // Close the connection as soon as the error message is sent.
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } catch (JSONException ex) {
            Logger.getLogger(EgaSecureConfigLogServiceHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
    //    FullHttpResponse response = new DefaultFullHttpResponse(
    //            HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
    //    response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
    //
    //    // Close the connection as soon as the error message is sent.
    //    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    //}

    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     *
     * @param ctx
     *            Context
     */
    private static void sendNotModified(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
        setDateHeader(response);

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sets the Date header for the HTTP response
     *
     * @param response
     *            HTTP response
     */
    private static void setDateHeader(FullHttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param response
     *            HTTP response
     * @param fileToCache
     *            file to extract content type
     */
    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(
                LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }
    private static void setDateAndCacheHeaders(HttpResponse response, String fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        Date x = new Date();
        x.setTime(System.currentTimeMillis()-1000000);
        response.headers().set(
                LAST_MODIFIED, dateFormatter.format(x));
        //response.headers().set(
        //        LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    /**
     * Sets the content type header for the HTTP Response
     *
     * @param response
     *            HTTP response
     * @param file
     *            file to extract content type
     */
    private static void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.headers().set(CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }
    private static void setContentTypeHeaderBinary(HttpResponse response) {
        response.headers().set(CONTENT_TYPE, "application/octet-stream");
    }
        
    // Generate JSON Header Section
    private JSONObject responseHeader(HttpResponseStatus status) throws JSONException {
        return responseHeader(status, "");
    }
    private JSONObject responseHeader(HttpResponseStatus status, String error) throws JSONException {
        JSONObject head = new JSONObject();
        
        head.put("apiVersion", "v2");
        head.put("code", String.valueOf(status.code()));
        head.put("service", "configlog");
        head.put("technicalMessage", "");                   // TODO (future)
        head.put("userMessage", status.reasonPhrase());
        head.put("errorCode", String.valueOf(status.code()));
        head.put("docLink", "http://www.ebi.ac.uk/ega");    // TODO (future)
        head.put("errorStack", error);                     // TODO ??
        
        return head;
    }
}

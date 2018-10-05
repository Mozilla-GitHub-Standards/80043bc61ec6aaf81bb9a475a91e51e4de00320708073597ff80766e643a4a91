package com.mozilla.secops.parser;

import com.google.api.client.json.JsonParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.logging.v2.model.LogEntry;
import com.google.api.services.logging.v2.model.HttpRequest;

import org.joda.time.DateTime;

import java.io.Serializable;
import java.io.IOException;
import java.util.Map;

public class GLB extends Payload<GLB> implements Serializable {
    private static final long serialVersionUID = 1L;
    private final JacksonFactory jf;

    private String requestMethod;
    private String userAgent;
    private String requestUrl;
    private String sourceAddress;

    @Override
    public Boolean matcher(String input) {
        try {
            JsonParser jp = jf.createJsonParser(input);
            LogEntry entry = jp.parse(LogEntry.class);
            Map<String,Object> m = entry.getJsonPayload();
            String eType = (String)m.get("@type");
            if (eType.equals("type.googleapis.com/google.cloud.loadbalancing.type.LoadBalancerLogEntry")) {
                return true;
            }
        } catch (IOException exc) {
            // pass
        }
        return false;
    }

    public GLB() {
        jf = new JacksonFactory();
    }

    public GLB(String input, Event e) {
        setType(Payload.PayloadType.GLB);

        jf = new JacksonFactory();
        LogEntry entry;
        try {
            JsonParser jp = jf.createJsonParser(input);
            entry = jp.parse(LogEntry.class);
        } catch (IOException exc) {
            return;
        }
        HttpRequest h = entry.getHttpRequest();
        if (h == null) {
            return;
        }

        String ets = entry.getTimestamp();
        if (ets != null) {
            DateTime d = Parser.parseISO8601(ets);
            if (d != null) {
                e.setTimestamp(d);
            }
        }

        sourceAddress = h.getRemoteIp();
        requestUrl = h.getRequestUrl();
        userAgent = h.getUserAgent();
        requestMethod = h.getRequestMethod();
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }
}
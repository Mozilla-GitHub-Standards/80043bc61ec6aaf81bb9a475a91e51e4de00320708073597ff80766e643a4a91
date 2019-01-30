package com.mozilla.secops.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.json.JsonParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.logging.v2.model.LogEntry;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

/**
 * Payload parser for nginx log data
 *
 * <p>This parser currently only supports nginx log data that has been encapsulated in the
 * jsonPayload section of a Stackdriver LogEntry.
 */
public class Nginx extends PayloadBase implements Serializable {
  private static final long serialVersionUID = 1L;

  private final JacksonFactory jfmatcher;

  private String xForwardedProto;
  private String remoteAddr;
  private String userAgent;
  private String referrer;
  private String request;
  private String remoteUser;
  private Double requestTime;
  private Integer bytesSent;
  private String trace;
  private Integer status;
  private String xForwardedFor;

  private String requestMethod;
  private String requestUrl;
  private String requestPath;

  @Override
  public Boolean matcher(String input, ParserState state) {
    try {
      // XXX We only support processing Stackdriver encapsulated nginx log entries
      // that are present in jsonPayload right now. This needs to be adjusted to support
      // for example raw nginx log entries.
      LogEntry entry = state.getLogEntryHint();
      if (entry == null) {
        JsonParser jp = jfmatcher.createJsonParser(input);
        entry = jp.parse(LogEntry.class);
      }

      Map<String, Object> m = entry.getJsonPayload();
      if (m == null) {
        return false;
      }

      // XXX This is not very efficient but there is otherwise no way to determine the
      // JSON payload type, as no field exists to indicate the type of log message. Check if
      // we have a few of the fields we want and indicate true of they are present.
      if ((m.get("remote_addr") != null)
          && (m.get("request") != null)
          && (m.get("bytes_sent") != null)
          && (m.get("request_time") != null)) {
        return true;
      }
    } catch (IOException exc) {
      // pass
    } catch (IllegalArgumentException exc) {
      // pass
    }
    return false;
  }

  @Override
  public Payload.PayloadType getType() {
    return Payload.PayloadType.NGINX;
  }

  /** Construct matcher object. */
  public Nginx() {
    jfmatcher = new JacksonFactory();
  }

  /**
   * Construct parser object.
   *
   * @param input Input string.
   * @param e Parent {@link Event}.
   * @param state State
   */
  public Nginx(String input, Event e, ParserState state) {
    jfmatcher = null;
    LogEntry entry = state.getLogEntryHint();
    if (entry == null) {
      // Use method local JacksonFactory as the object is not serializable, and this event
      // may be passed around
      JacksonFactory jf = new JacksonFactory();
      try {
        JsonParser jp = jf.createJsonParser(input);
        entry = jp.parse(LogEntry.class);
      } catch (IOException exc) {
        return;
      }
    }

    Map<String, Object> m = entry.getJsonPayload();
    if (m == null) {
      return;
    }

    String pbuf = null;
    try {
      ObjectMapper mapper = new ObjectMapper();
      pbuf = mapper.writeValueAsString(m);
    } catch (JsonProcessingException exc) {
      return;
    }
    if (pbuf == null) {
      return;
    }

    com.mozilla.secops.parser.models.nginxstackdriver.NginxStackdriver nginxs;
    try {
      ObjectMapper mapper = new ObjectMapper();
      nginxs =
          mapper.readValue(
              pbuf, com.mozilla.secops.parser.models.nginxstackdriver.NginxStackdriver.class);
    } catch (IOException exc) {
      return;
    }

    xForwardedProto = nginxs.getXForwardedProto();
    remoteAddr = nginxs.getRemoteAddr();
    userAgent = nginxs.getUserAgent();
    referrer = nginxs.getReferrer();
    request = nginxs.getRequest();
    remoteUser = nginxs.getRemoteUser();
    requestTime = nginxs.getRequestTime();
    bytesSent = nginxs.getBytesSent();
    trace = nginxs.getTrace();
    status = new Integer(nginxs.getStatus());
    xForwardedFor = nginxs.getXForwardedFor();

    if (request != null) {
      String[] parts = request.split(" ");
      if (parts.length == 3) {
        requestMethod = parts[0];
        requestUrl = parts[1];
      }
    }

    if (requestUrl != null) {
      String[] parts = requestUrl.split("\\?");
      if (parts.length > 1) {
        requestPath = parts[0];
      } else {
        requestPath = requestUrl;
      }
    }
  }

  @Override
  public String eventStringValue(EventFilterPayload.StringProperty property) {
    switch (property) {
      case NGINX_REQUESTMETHOD:
        return requestMethod;
      case NGINX_URLREQUESTPATH:
        return requestPath;
    }
    return null;
  }

  @Override
  public Integer eventIntegerValue(EventFilterPayload.IntegerProperty property) {
    switch (property) {
      case NGINX_STATUS:
        return status;
    }
    return null;
  }

  /**
   * Get request URL.
   *
   * @return Request URL string.
   */
  public String getRequestUrl() {
    return requestUrl;
  }

  /**
   * Get request path.
   *
   * @return Request path string.
   */
  public String getRequestPath() {
    return requestPath;
  }

  /**
   * Get user agent.
   *
   * @return User agent string.
   */
  public String getUserAgent() {
    return userAgent;
  }

  /**
   * Get request method.
   *
   * @return Request method string.
   */
  public String getRequestMethod() {
    return requestMethod;
  }

  /**
   * Get request.
   *
   * @return Request string.
   */
  public String getRequest() {
    return request;
  }

  /**
   * Get source address.
   *
   * @return Source address string.
   */
  public String getSourceAddress() {
    return remoteAddr;
  }

  /**
   * Get status.
   *
   * @return status integer.
   */
  public Integer getStatus() {
    return status;
  }

  /**
   * Get X forwarded for
   *
   * @return XFF string.
   */
  public String getXForwardedFor() {
    return xForwardedFor;
  }

  /**
   * Get referrer
   *
   * @return Referrer string.
   */
  public String getReferrer() {
    return referrer;
  }
}
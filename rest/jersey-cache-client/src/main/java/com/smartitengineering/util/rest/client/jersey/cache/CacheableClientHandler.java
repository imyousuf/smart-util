/*
 * This is a utility project for wide range of applications
 *
 * Copyright (C) 2010  Imran M Yousuf (imyousuf@smartitengineering.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  10-1  USA
 */
package com.smartitengineering.util.rest.client.jersey.cache;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.TerminatingClientHandler;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.client.apache.ApacheHttpMethodExecutor;
import com.sun.jersey.client.apache.DefaultApacheHttpMethodExecutor;
import com.sun.jersey.client.apache.config.ApacheHttpClientConfig;
import com.sun.jersey.core.header.InBoundHeaders;
import com.sun.jersey.core.util.ReaderWriter;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.commons.httpclient.HttpClient;
import org.codehaus.httpcache4j.Challenge;
import org.codehaus.httpcache4j.HTTPMethod;
import org.codehaus.httpcache4j.HTTPRequest;
import org.codehaus.httpcache4j.HTTPResponse;
import org.codehaus.httpcache4j.Header;
import org.codehaus.httpcache4j.Headers;
import org.codehaus.httpcache4j.MIMEType;
import org.codehaus.httpcache4j.UsernamePasswordChallenge;
import org.codehaus.httpcache4j.cache.CacheStorage;
import org.codehaus.httpcache4j.cache.HTTPCache;
import org.codehaus.httpcache4j.cache.MemoryCacheStorage;
import org.codehaus.httpcache4j.payload.ByteArrayPayload;
import org.codehaus.httpcache4j.payload.Payload;
import org.codehaus.httpcache4j.resolver.ResponseResolver;

/**
 *
 * @author imyousuf
 */
public class CacheableClientHandler
    extends TerminatingClientHandler {

  private final HTTPCache cache;
  private final boolean internalResolver;
  private static final ThreadLocal<ClientRequest> REQUEST_HOLDER = new ThreadLocal<ClientRequest>();
  private final ApacheHttpMethodExecutor methodProcessor;

  public CacheableClientHandler(HttpClient httpClient, ClientConfig clientConfig) {
    this(httpClient, clientConfig, new MemoryCacheStorage());
  }

  public CacheableClientHandler(HttpClient httpClient, ClientConfig clientConfig,
                                CacheStorage storage) {
    this(storage, getDefaultResponseResolver(httpClient, clientConfig, REQUEST_HOLDER));
    httpClient.getParams().setAuthenticationPreemptive(clientConfig.getPropertyAsFeature(
        ApacheHttpClientConfig.PROPERTY_PREEMPTIVE_AUTHENTICATION));
    final Integer connectTimeout = (Integer) clientConfig.getProperty(ApacheHttpClientConfig.PROPERTY_CONNECT_TIMEOUT);
    if (connectTimeout != null) {
      httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(connectTimeout);
    }

  }

  public CacheableClientHandler(CacheStorage storage, ResponseResolver responseResolver) {
    this(storage, getAsEntry(responseResolver, null));
  }

  public CacheableClientHandler(CacheStorage storage, Entry<ResponseResolver, ApacheHttpMethodExecutor> entry) {
    this.methodProcessor = entry.getValue();
    this.cache = new HTTPCache(storage, entry.getKey());
    this.internalResolver = entry.getKey() instanceof CustomApacheHttpClientResponseResolver;
  }

  public static Entry<ResponseResolver, ApacheHttpMethodExecutor> getDefaultResponseResolver(HttpClient client,
                                                                                             ClientConfig config,
                                                                                             ThreadLocal<ClientRequest> requestHolder) {
    ApacheHttpMethodExecutor methodProcessor = new DefaultApacheHttpMethodExecutor(client);
    ResponseResolver responseResolver = new CustomApacheHttpClientResponseResolver(methodProcessor, REQUEST_HOLDER);
    return getAsEntry(responseResolver, methodProcessor);
  }

  protected static Entry<ResponseResolver, ApacheHttpMethodExecutor> getAsEntry(ResponseResolver responseResolver,
                                                                                ApacheHttpMethodExecutor methodProcessor) {
    return new SimpleEntry<ResponseResolver, ApacheHttpMethodExecutor>(responseResolver, methodProcessor);
  }

  @Override
  public ClientResponse handle(ClientRequest cr)
      throws ClientHandlerException {
    final HTTPMethod method = HTTPMethod.valueOf(cr.getMethod());
    HTTPRequest request = processRequest(cr, method);
    if (internalResolver) {
      REQUEST_HOLDER.set(cr);
    }
    HTTPResponse cachedResponse = cache.doCachedRequest(request);
    if (internalResolver) {
      REQUEST_HOLDER.remove();
    }
    Headers headers = cachedResponse.getHeaders();
    InBoundHeaders inBoundHeaders = getInBoundHeaders(headers);
    final InputStream entity = getEntityStream(cachedResponse);
    ClientResponse response = new ClientResponse(cachedResponse.getStatus().getCode(), inBoundHeaders, entity,
                                                 getMessageBodyWorkers());
    return response;
  }

  public HTTPCache getCache() {
    return cache;
  }

  protected InputStream getEntityStream(HTTPResponse cachedResponse) {
    final InputStream entity;
    if (cachedResponse.hasPayload()) {
      final InputStream inputStream = cachedResponse.getPayload().getInputStream();
      if (inputStream.markSupported()) {
        entity = inputStream;
      }
      else {
        entity = new BufferedInputStream(inputStream, ReaderWriter.BUFFER_SIZE);
      }
    }
    else {
      entity = new ByteArrayInputStream(new byte[0]);
    }
    return entity;
  }

  protected InBoundHeaders getInBoundHeaders(Headers headers) {
    InBoundHeaders inBoundHeaders = new InBoundHeaders();
    for (Header header : headers) {
      List<String> list = inBoundHeaders.get(header.getName());
      if (list == null) {
        list = new ArrayList<String>();
      }
      list.add(header.getValue());
      inBoundHeaders.put(header.getName(), list);
    }
    return inBoundHeaders;
  }

  protected HTTPRequest processRequest(ClientRequest cr,
                                       final HTTPMethod method) {
    HTTPRequest request = new HTTPRequest(cr.getURI(), method);
    if (!internalResolver) {
      final Map<String, Object> props = cr.getProperties();
      /*
       * Add authorization challenge
       */
      if (props.containsKey(CacheableClientConfigProps.USERNAME) &&
          props.containsKey(CacheableClientConfigProps.PASSWORD)) {
        final String username = (String) props.get(CacheableClientConfigProps.USERNAME);
        final String password = (String) props.get(CacheableClientConfigProps.PASSWORD);
        Challenge challenge =
                  new UsernamePasswordChallenge(username, password);
        request = request.challenge(challenge);
      }
      /*
       * Copy payload set into the request if any
       */
      if (cr.getEntity() != null) {
        final RequestEntityWriter requestEntityWriter = getRequestEntityWriter(cr);
        final MIMEType mimeType = new MIMEType(requestEntityWriter.getMediaType().getType(), requestEntityWriter.
            getMediaType().getSubtype());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
          requestEntityWriter.writeRequestEntity(outputStream);
          Payload payload = new ByteArrayPayload(new ByteArrayInputStream(outputStream.toByteArray()), mimeType);
          request = request.payload(payload);
        }
        catch (IOException ex) {
          throw new ClientHandlerException(ex);
        }
      }
    }
    /*
     * Copy headers set by user explicitly
     */
    Headers requestHeaders = new Headers();
    MultivaluedMap<String, Object> map = cr.getHeaders();
    for (String key : map.keySet()) {
      List<Object> values = map.get(key);
      ArrayList<Header> headers = new ArrayList<Header>(values.size());
      for (Object value : values) {
        Header header = new Header(key, ClientRequest.getHeaderValue(value));
        headers.add(header);
      }
      requestHeaders = requestHeaders.add(key, headers);
    }
    request = request.headers(requestHeaders);
    return request;
  }

  public ApacheHttpMethodExecutor getMethodProcessor() {
    return methodProcessor;
  }
}

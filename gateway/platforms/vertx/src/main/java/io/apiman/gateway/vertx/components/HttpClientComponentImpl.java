/*
 * Copyright 2014 JBoss Inc
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
package io.apiman.gateway.vertx.components;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;

import io.apiman.gateway.engine.async.AsyncResultImpl;
import io.apiman.gateway.engine.async.IAsyncResultHandler;
import io.apiman.gateway.engine.components.IHttpClientComponent;
import io.apiman.gateway.engine.components.http.HttpMethod;
import io.apiman.gateway.engine.components.http.IHttpClientRequest;
import io.apiman.gateway.engine.components.http.IHttpClientResponse;
import io.apiman.gateway.vertx.config.VertxEngineConfig;
import io.apiman.gateway.vertx.i18n.Messages;

/**
 * A Vert.x based implementation of {@link IHttpClientComponent}. Ensure that
 * {@link IHttpClientRequest#end()} is called after writing is finished, or your data may never be sent, and
 * the connection will be left hanging.
 * 
 * @author Marc Savy <msavy@redhat.com>
 */
public class HttpClientComponentImpl implements IHttpClientComponent {

    private Vertx vertx;
    private IAsyncResultHandler<IHttpClientResponse> responseHandler;

    public HttpClientComponentImpl(Vertx vertx, VertxEngineConfig engineConfig, Map<String, String> config) {
        this.vertx = vertx;
    }

    @Override
    public IHttpClientRequest request(String endpoint, HttpMethod method,
            IAsyncResultHandler<IHttpClientResponse> handler) {
        this.responseHandler = handler;

        URL pEndpoint = parseEndpoint(endpoint);

        HttpClient client = vertx.createHttpClient().
                setHost(pEndpoint.getHost()).
                setPort(pEndpoint.getPort());

        HttpClientRequest request = client.request(method.toString(), 
                pEndpoint.getPath(),
                new HttpClientResponseImpl());

        return new HttpClientRequestImpl(request);
    }

    private class HttpClientResponseImpl implements IHttpClientResponse, Handler<HttpClientResponse> {

        private HttpClientResponse response;
        private Buffer body;

        @Override
        public void handle(HttpClientResponse response) {
            this.response = response;

            // The interface stipulates accumulating the whole body,
            response.bodyHandler(new Handler<Buffer>() {

                @Override
                public void handle(Buffer wholeBody) {
                    body = wholeBody;
                }
            });

            response.endHandler(new VoidHandler() {

                @Override
                public void handle() {
                    responseHandler.handle(AsyncResultImpl
                            .<IHttpClientResponse> create(HttpClientResponseImpl.this));
                }
            });
        }

        @Override
        public int getResponseCode() {
            return response.statusCode();
        }

        @Override
        public String getResponseMessage() {
            return response.statusMessage();
        }

        @Override
        public String getHeader(String headerName) {
            return response.headers().get(headerName);
        }

        @Override
        public String getBody() {
            return body.toString();
        }

        @Override
        // Doesn't make sense in async world.
        public void close() {
        }

    }

    class HttpClientRequestImpl implements IHttpClientRequest {

        private boolean finished = false;
        private HttpClientRequest request;

        public HttpClientRequestImpl(HttpClientRequest request) {
            this.request = request;
        }

        @Override
        public void addHeader(String headerName, String headerValue) {
            request.putHeader(headerName, headerValue);
        }

        @Override
        public void removeHeader(String headerName) {
            request.headers().remove(headerName);
        }

        @Override
        public void write(byte[] data) {
            if (finished) {
                throw new IllegalStateException(Messages.getString("HttpClientComponentImpl.0")); //$NON-NLS-1$
            }

            request.write(new Buffer(data));
        }

        @Override
        public void write(String body, String charsetName) {
            if (finished) {
                throw new IllegalStateException(Messages.getString("HttpClientComponentImpl.0")); //$NON-NLS-1$
            }

            request.write(new Buffer(body, charsetName));
        }

        @Override
        public void end() {
            request.end();
            finished = true;
        }
    }

    private URL parseEndpoint(String endpoint) {
        try {
            return new URL(endpoint);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}

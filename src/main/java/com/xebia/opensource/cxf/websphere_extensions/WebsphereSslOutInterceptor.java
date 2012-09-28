/*
 * Copyright 2010-2012 Xebia b.v.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.xebia.opensource.cxf.websphere_extensions;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.http.HTTPConduit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class WebsphereSslOutInterceptor extends AbstractPhaseInterceptor<Message> {
  private static final Logger LOGGER = LoggerFactory.getLogger(WebsphereSslOutInterceptor.class);
  private static final String HTTPS_PROTOCOL_NAME = "https";

  private final WebsphereSslSocketFactoryLocator locator = createLocator();

  private String sslAlias = null;

  public WebsphereSslOutInterceptor() {
    super(Phase.SETUP);
  }

  public void handleMessage(Message message) throws Fault {
    if (!locator.isEnabled()) {
      return;
    }

    Conduit conduit = message.getExchange().getConduit(message);
    if (!(conduit instanceof HTTPConduit)) {
      // SSL config only applies to HTTPConduit, early-out.
      return;
    }

    HTTPConduit httpConduit = (HTTPConduit) conduit;

    String endpoint = (String) message.get(Message.ENDPOINT_ADDRESS);
    if (endpoint == null) {
      LOGGER.warn("Null endpoint address encountered, unable to appy SSL configuration");
      return;
    }

    try {
      URL endpointUrl = new URL(endpoint);

      if (supports(endpointUrl)) {
        TLSClientParameters tlsClientParameters = getOrCreateAndSetTLSClientParameters(httpConduit);
        tlsClientParameters.setSSLSocketFactory(locator.getSslFactory(sslAlias, endpointUrl));
      }
    } catch (Exception e) {
      LOGGER.error("Got an exception getting the Websphere SSL Socket Factory: '{}'.", e.getMessage());
      throw new Fault(e);
    }
  }

  // Relaxed visibility for testing
  boolean supports(URL endpointUrl) {
    return HTTPS_PROTOCOL_NAME.equalsIgnoreCase(endpointUrl.getProtocol());
  }

  private TLSClientParameters getOrCreateAndSetTLSClientParameters(HTTPConduit httpConduit) {
    TLSClientParameters tlsClientParameters = httpConduit.getTlsClientParameters();
    if (tlsClientParameters == null) {
      tlsClientParameters = new TLSClientParameters();
      httpConduit.setTlsClientParameters(tlsClientParameters);
    }
    return tlsClientParameters;
  }

  // Relaxed visibility for testing
  WebsphereSslSocketFactoryLocator createLocator() {
    return WebsphereSslSocketFactoryLocator.getInstance();
  }

  public void setSslAlias(String sslAlias) {
    this.sslAlias = sslAlias;
  }
}
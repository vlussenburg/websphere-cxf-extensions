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
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.HTTPConduit;
import org.junit.Test;

import javax.net.ssl.HttpsURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static junit.framework.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebsphereSslOutInterceptorTest {

  public static final String FAULTY_HELPER_FULL_QUALIFIED_CLASSNAME = FaultyJSSEHelper.class.getName();

  @Test
  public void testThatClassDoesntInterfereOnNonWasMachine() {
    WebsphereSslOutInterceptor interceptor = new WebsphereSslOutInterceptor() {
      @Override
      WebsphereSslSocketFactoryLocator createLocator() {
        return new WebsphereSslSocketFactoryLocator(null, null, null, null, null, null);
      }
    };

    interceptor.handleMessage(null);
  }

  @Test
  public void testHappyFlowInWASContainer() {
    Message message = mock(Message.class);
    Exchange exchange = mock(Exchange.class);
    when(message.getExchange()).thenReturn(exchange);

    HTTPConduit conduit = mock(HTTPConduit.class);
    when(exchange.getConduit(message)).thenReturn(conduit);

    TLSClientParameters tlsClientParameters = new TLSClientParameters();
    when(conduit.getTlsClientParameters()).thenReturn(tlsClientParameters);
    when(message.get(Message.ENDPOINT_ADDRESS)).thenReturn("https://localhost");

    new WebsphereSslOutInterceptor().handleMessage(message);

    // Since our dummy implementation returns the default ssl socket factory
    assertEquals(tlsClientParameters.getSSLSocketFactory(), HttpsURLConnection.getDefaultSSLSocketFactory());
  }

  @Test
  public void testNonHttpsFlowInWASContainer() {
    Message message = mock(Message.class);
    Exchange exchange = mock(Exchange.class);
    when(message.getExchange()).thenReturn(exchange);

    HTTPConduit conduit = mock(HTTPConduit.class);
    when(exchange.getConduit(message)).thenReturn(conduit);

    TLSClientParameters tlsClientParameters = new TLSClientParameters();
    when(conduit.getTlsClientParameters()).thenReturn(tlsClientParameters);
    when(message.get(Message.ENDPOINT_ADDRESS)).thenReturn("http://localhost");

    new WebsphereSslOutInterceptor().handleMessage(message);

    // http, insecure, so not set!
    assertNull(tlsClientParameters.getSSLSocketFactory());
  }

  @Test
  public void invalidOrChangedJSSEHelperOutputsExceptionWithUsefulMessage() {
    try {
      // Override to JSSEClass that exists, but misses the constants and methods expected.
      new WebsphereSslOutInterceptor() {
        @Override
        WebsphereSslSocketFactoryLocator createLocator() {
          return WebsphereSslSocketFactoryLocator.getInstanceWithClass(FAULTY_HELPER_FULL_QUALIFIED_CLASSNAME);
        }
      };

      fail();
    } catch (RuntimeException e) {
      assertEquals(FAULTY_HELPER_FULL_QUALIFIED_CLASSNAME + " found, but unable to create Websphere-specific configuration.", e.getMessage());
    }
  }

  @Test
  public void testOnlyHttpsIsSupported() throws MalformedURLException {
    WebsphereSslOutInterceptor interceptor = new WebsphereSslOutInterceptor() {
      @Override
      WebsphereSslSocketFactoryLocator createLocator() {
        return new WebsphereSslSocketFactoryLocator(null, null, null, null, null, null);
      }
    };

    assertFalse(interceptor.supports(new URL("ftp://hallo")));
    assertFalse(interceptor.supports(new URL("http://hallo")));
    assertTrue(interceptor.supports(new URL("https://hallo")));
  }

  @SuppressWarnings("unused")
  private final static class FaultyJSSEHelper {
  }
}

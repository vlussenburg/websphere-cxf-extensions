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
package com.xebia.opensource.cxf.extensions.websphere;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.HTTPConduit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.xebia.opensource.cxf.extensions.websphere.WebSphereSSLOutInterceptor;
import com.xebia.opensource.cxf.extensions.websphere.WebSphereSSLSocketFactoryLocator;

@RunWith(MockitoJUnitRunner.class)
public class WebsphereSSLOutInterceptorTest {

	public static final String FAULTY_HELPER_FULL_QUALIFIED_CLASSNAME = FaultyJSSEHelper.class.getName();

	@Mock
	private Message message;
	
	@Mock
	private Exchange exchange;
	
	@Mock
	private HTTPConduit conduit;
	
	@Before
	public void setUp() {
		when(message.getExchange()).thenReturn(exchange);
		when(exchange.getConduit(message)).thenReturn(conduit);
	}
	
	@Test
	public void testThatClassDoesntInterfereOnNonWasMachine() {
		
		WebSphereSSLOutInterceptor interceptor = new WebSphereSSLOutInterceptor() {
			
			@Override
			WebSphereSSLSocketFactoryLocator createLocator() {
				return new WebSphereSSLSocketFactoryLocator(null, null, null, null, null, null);
			}
		};

		interceptor.handleMessage(null);
	}

	@Test
	public void testHappyFlowInWASContainer() {

		TLSClientParameters tlsClientParameters = new TLSClientParameters();
		
		when(conduit.getTlsClientParameters()).thenReturn(tlsClientParameters);
		when(message.get(Message.ENDPOINT_ADDRESS)).thenReturn("https://localhost");

		new WebSphereSSLOutInterceptor().handleMessage(message);

		// Since our dummy implementation returns the default ssl socket factory
		assertEquals(tlsClientParameters.getSSLSocketFactory(),	HttpsURLConnection.getDefaultSSLSocketFactory());
	}

	@Test
	public void testNonHttpsFlowInWASContainer() {

		TLSClientParameters tlsClientParameters = new TLSClientParameters();
		
		when(conduit.getTlsClientParameters()).thenReturn(tlsClientParameters);
		when(message.get(Message.ENDPOINT_ADDRESS)).thenReturn("http://localhost");

		new WebSphereSSLOutInterceptor().handleMessage(message);

		// http, insecure, so not set!
		assertNull(tlsClientParameters.getSSLSocketFactory());
	}

	@Test
	public void invalidOrChangedJSSEHelperOutputsExceptionWithUsefulMessage() {
		
		try {
			
			// Override to JSSEClass that exists, but misses the constants and
			// methods expected.
			new WebSphereSSLOutInterceptor() {
				
				@Override
				WebSphereSSLSocketFactoryLocator createLocator() {
					return WebSphereSSLSocketFactoryLocator.getInstanceWithClass(FAULTY_HELPER_FULL_QUALIFIED_CLASSNAME);
				}
			};

			fail();
			
		} catch (RuntimeException runtimeException) {
			assertEquals(FAULTY_HELPER_FULL_QUALIFIED_CLASSNAME + " found, but unable to create Websphere-specific configuration.", runtimeException.getMessage());
		}
	}

	@Test
	public void testOnlyHttpsIsSupported() throws MalformedURLException {
		
		WebSphereSSLOutInterceptor interceptor = new WebSphereSSLOutInterceptor() {
			
			@Override
			WebSphereSSLSocketFactoryLocator createLocator() {
				return new WebSphereSSLSocketFactoryLocator(null, null, null, null, null, null);
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

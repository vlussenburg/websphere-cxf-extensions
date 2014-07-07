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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This locator contains all the Websphere-specific stuff.
 *
 * @link http://stackoverflow.com/questions/7275063/how-to-set-up-apache-cxf-client-to-use-websphere-truststore-receiving-no-trus
 */
class WebSphereSSLSocketFactoryLocator {

	private static final int PORT_UNDEFINED = -1;

	private static final String IBM_SSL_CONFIG_CHANGE_LISTENER = "com.ibm.websphere.ssl.SSLConfigChangeListener";
	
	private static final String IBM_JSSE_HELPER = "com.ibm.websphere.ssl.JSSEHelper";

	private static final Logger LOGGER = LoggerFactory.getLogger(WebSphereSSLOutInterceptor.class);

	private final String connectionInfoRemoteHost;
	
	private final String connectionInfoRemotePort;
	
	private final String connectionInfoDirection;
	
	private final String connectionInfoDirectionOutbound;
	
	private final Object jsseHelper;
	
	private final Method getSSLSocketFactoryMethod;

	// Relaxed visibility for testing
	WebSphereSSLSocketFactoryLocator(
		final String connectionInfoRemoteHost, 
		final String connectionInfoRemotePort, 
		final String connectionInfoDirection, 
		final String connectionInfoDirectionOutbound, 
		final Object jsseHelper, 
		final Method getSSLSocketFactoryMethod) {
		
		this.connectionInfoRemoteHost = connectionInfoRemoteHost;
		this.connectionInfoRemotePort = connectionInfoRemotePort;
		this.connectionInfoDirection = connectionInfoDirection;
		this.connectionInfoDirectionOutbound = connectionInfoDirectionOutbound;
		this.jsseHelper = jsseHelper;
		this.getSSLSocketFactoryMethod = getSSLSocketFactoryMethod;
	}

	private WebSphereSSLSocketFactoryLocator() {
		this(null, null, null, null, null, null);
	}

	public static WebSphereSSLSocketFactoryLocator getInstance() {
		return WebSphereSSLSocketFactoryLocator.getInstanceWithClass(IBM_JSSE_HELPER);
	}

	/**
	 * Locator is enabled if Websphere classes were found on the Classpath.
	 */
	public boolean isEnabled() {
		return jsseHelper != null;
	}

	public SSLSocketFactory getSslFactory(final String sslAlias, final URL endpointUrl)	throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		
		final SSLSocketFactory factory = getSslSocketFactory(sslAlias, getConnectionInfo(endpointUrl));

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Factory returned by JSSEHelper: {}", factory);			
		}

		return factory;
	}

	// Relaxed visibility for testing
	static WebSphereSSLSocketFactoryLocator getInstanceWithClass(final String ibmJsseHelperClass) {
		
		final ClassLoader classLoader = WebSphereSSLSocketFactoryLocator.class.getClassLoader();

		final Class<?> jsseHelperClazz;
		
		try {
			jsseHelperClazz = classLoader.loadClass(ibmJsseHelperClass);
		} catch (final ClassNotFoundException classNotFoundException) {
			if (LOGGER.isInfoEnabled()) {
				LOGGER.info("Unable to load {} class. Proceeding with non-Websphere SSL configuration.", ibmJsseHelperClass);				
			}
			return new WebSphereSSLSocketFactoryLocator();
		}

		try {
			
			final String connectionInfoRemoteHost = getConstant(jsseHelperClazz, "CONNECTION_INFO_REMOTE_HOST");
			final String connectionInfoRemotePort = getConstant(jsseHelperClazz, "CONNECTION_INFO_REMOTE_PORT");
			final String connectionInfoDirection = getConstant(jsseHelperClazz, "CONNECTION_INFO_DIRECTION");
			final String connectionInfoDirectionOutbound = getConstant(jsseHelperClazz, "DIRECTION_OUTBOUND");
			
			final Method method = jsseHelperClazz.getMethod("getInstance");
			
			final Object jsseHelper = method.invoke(null);

			final Class<?> configListenerClazz = classLoader.loadClass(IBM_SSL_CONFIG_CHANGE_LISTENER);
			
			final Method getSSLSocketFactoryMethod = jsseHelperClazz.getMethod("getSSLSocketFactory", new Class[] {
				String.class, 
				Map.class, 
				configListenerClazz
			});

			if (LOGGER.isInfoEnabled()) {
				LOGGER.info("Successfully initialized CXF interceptor with Websphere SSL configuration.");				
			}
			
			return new WebSphereSSLSocketFactoryLocator(
				connectionInfoRemoteHost, 
				connectionInfoRemotePort, 
				connectionInfoDirection, 
				connectionInfoDirectionOutbound, 
				jsseHelper, 
				getSSLSocketFactoryMethod
			);
		} catch (final Exception exception) {
			throw new RuntimeException(ibmJsseHelperClass + " found, but unable to create Websphere-specific configuration.", exception);
		}
	}

	private Map<String, String> getConnectionInfo(final URL endpointUrl) {
		
		final Map<String, String> connectionInfo = new HashMap<String, String>();

		connectionInfo.put(connectionInfoDirection,	connectionInfoDirectionOutbound);
		connectionInfo.put(connectionInfoRemoteHost, endpointUrl.getHost());
		
		final int portToSet = (endpointUrl.getPort() == PORT_UNDEFINED) ? endpointUrl.getDefaultPort() : endpointUrl.getPort();
		
		connectionInfo.put(connectionInfoRemotePort, String.valueOf(portToSet));
		
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Connection info to be passed to the JSSEHelper: {}", connectionInfo);		
		}
		
		return connectionInfo;
	}

	private SSLSocketFactory getSslSocketFactory(final String sslAlias, final Map<String, String> connectionInfo) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		return (SSLSocketFactory) getSSLSocketFactoryMethod.invoke(jsseHelper, new Object[] {sslAlias, connectionInfo, null});
	}

	private static String getConstant(final Class<?> clazz, final String constant) throws IllegalAccessException, NoSuchFieldException {
		return (String) clazz.getDeclaredField(constant).get(String.class);
	}
}

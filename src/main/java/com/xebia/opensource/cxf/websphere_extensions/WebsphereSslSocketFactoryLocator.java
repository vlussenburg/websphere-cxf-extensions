package com.xebia.opensource.cxf.websphere_extensions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * This locator contains all the Websphere-specific stuff.
 *
 * @link http://stackoverflow.com/questions/7275063/how-to-set-up-apache-cxf-client-to-use-websphere-truststore-receiving-no-trus
 */
class WebsphereSslSocketFactoryLocator {
  private static final Logger LOGGER = LoggerFactory.getLogger(WebsphereSslOutInterceptor.class);

  private static final int PORT_UNDEFINED = -1;

  private static final String IBM_SSL_CONFIG_CHANGE_LISTENER = "com.ibm.websphere.ssl.SSLConfigChangeListener";
  private static final String IBM_JSSE_HELPER = "com.ibm.websphere.ssl.JSSEHelper";

  private final String connectionInfoRemoteHost;
  private final String connectionInfoRemotePort;
  private final String connectionInfoDirection;
  private final String connectionInfoDirectionOutbound;
  private final Object jsseHelper;
  private final Method getSSLSocketFactoryMethod;

  public WebsphereSslSocketFactoryLocator(String connectionInfoRemoteHost, String connectionInfoRemotePort, String connectionInfoDirection, String connectionInfoDirectionOutbound,
                                          Object jsseHelper, Method getSSLSocketFactoryMethod) {
    this.connectionInfoRemoteHost = connectionInfoRemoteHost;
    this.connectionInfoRemotePort = connectionInfoRemotePort;
    this.connectionInfoDirection = connectionInfoDirection;
    this.connectionInfoDirectionOutbound = connectionInfoDirectionOutbound;
    this.jsseHelper = jsseHelper;
    this.getSSLSocketFactoryMethod = getSSLSocketFactoryMethod;
  }

  private WebsphereSslSocketFactoryLocator() {
    this(null, null, null, null, null, null);
  }

  public static WebsphereSslSocketFactoryLocator getInstance() {
    return WebsphereSslSocketFactoryLocator.getInstanceWithClass(IBM_JSSE_HELPER);
  }

  /**
   * Locator is enabled if Websphere classes were found on the Classpath.
   */
  public boolean isEnabled() {
    return jsseHelper != null;
  }

  public SSLSocketFactory getSslFactory(String sslAlias, URL endpointUrl) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
    SSLSocketFactory factory = getSslSocketFactory(sslAlias, getConnectionInfo(endpointUrl));

    LOGGER.debug("Factory returned by JSSEHelper: {}", factory);

    return factory;
  }

  // Relaxed visibility for testing
  static WebsphereSslSocketFactoryLocator getInstanceWithClass(String ibmJsseHelperClass) {
    ClassLoader classLoader = WebsphereSslSocketFactoryLocator.class.getClassLoader();

    final Class<?> jsseHelperClazz;
    try {
      jsseHelperClazz = classLoader.loadClass(ibmJsseHelperClass);
    } catch (ClassNotFoundException e) {
      LOGGER.info("Unable to load {} class. Proceeding with non-Websphere SSL configuration.", ibmJsseHelperClass);
      return new WebsphereSslSocketFactoryLocator();
    }

    try {
      String connectionInfoRemoteHost = getConstant(jsseHelperClazz, "CONNECTION_INFO_REMOTE_HOST");
      String connectionInfoRemotePort = getConstant(jsseHelperClazz, "CONNECTION_INFO_REMOTE_PORT");
      String connectionInfoDirection = getConstant(jsseHelperClazz, "CONNECTION_INFO_DIRECTION");
      String connectionInfoDirectionOutbound = getConstant(jsseHelperClazz, "DIRECTION_OUTBOUND");
      Method method = jsseHelperClazz.getMethod("getInstance");
      Object jsseHelper = method.invoke(null);

      Class<?> configListenerClazz = classLoader.loadClass(IBM_SSL_CONFIG_CHANGE_LISTENER);
      Method getSSLSocketFactoryMethod = jsseHelperClazz.getMethod("getSSLSocketFactory", new Class[]{String.class, Map.class, configListenerClazz});

      LOGGER.info("Successfully initialized CXF interceptor with Websphere SSL configuration.");
      return new WebsphereSslSocketFactoryLocator(connectionInfoRemoteHost, connectionInfoRemotePort, connectionInfoDirection, connectionInfoDirectionOutbound, jsseHelper, getSSLSocketFactoryMethod);
    } catch (Exception e) {
      throw new RuntimeException(ibmJsseHelperClass + " found, but unable to create Websphere-specific configuration.", e);
    }
  }

  private Map<String, String> getConnectionInfo(URL endpointUrl) {
    Map<String, String> connectionInfo = new HashMap<String, String>();

    connectionInfo.put(connectionInfoDirection, connectionInfoDirectionOutbound);
    connectionInfo.put(connectionInfoRemoteHost, endpointUrl.getHost());
    int portToSet = (endpointUrl.getPort() == PORT_UNDEFINED) ? endpointUrl.getDefaultPort() : endpointUrl.getPort();
    connectionInfo.put(connectionInfoRemotePort, "" + portToSet);

    LOGGER.debug("Connection info to be passed to the JSSEHelper: {}", connectionInfo);
    return connectionInfo;
  }

  private SSLSocketFactory getSslSocketFactory(String sslAlias, Map<String, String> connectionInfo) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
    return (SSLSocketFactory) getSSLSocketFactoryMethod.invoke(jsseHelper, new Object[]{sslAlias, connectionInfo, null});
  }

  private static String getConstant(Class<?> clazz, String constant) throws IllegalAccessException, NoSuchFieldException {
    return (String) clazz.getDeclaredField(constant).get(String.class);
  }
}

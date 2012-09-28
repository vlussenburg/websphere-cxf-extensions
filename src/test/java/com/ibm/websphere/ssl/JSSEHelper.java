package com.ibm.websphere.ssl;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.util.Map;

public class JSSEHelper {

  public final static String CONNECTION_INFO_REMOTE_HOST = "";
  public final static String CONNECTION_INFO_REMOTE_PORT = "";
  public final static String CONNECTION_INFO_DIRECTION = "";
  public final static String DIRECTION_OUTBOUND = "";

  public static JSSEHelper getInstance() {
    return new JSSEHelper();
  }

  public SSLSocketFactory getSSLSocketFactory(String alias, Map<String, String> connectionInfo, SSLConfigChangeListener listener) {
    return HttpsURLConnection.getDefaultSSLSocketFactory();
  }
}

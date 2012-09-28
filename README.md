websphere-cxf-extensions
========================

CXF extensions specific for IBM® Websphere®.

## The WebsphereSslOutInterceptor

### Overview
This interceptor is meant to interact with the SslSocketFactory that is configured within the Websphere Application Server® for outgoing messages. By using this interceptor, CXF uses all settings, client certificates, truststore and so on configured within the application server, thus preventing the need for application-specific SSL configuration.

### Other application server compatibility
This out interceptor does interact with classes only included in the IBM® JDK, but it is implemented using reflection and has no hard runtime dependencies on IBM® classes (similar to how Spring does this for appserver specific code). If not running within Websphere®, the interceptor simply logs it is unable to do any work. So, you can include this statically in your build and still use your application on Tomcat/Jetty/Glassfish and so on.

### Based on...
The SSL out interceptor has been based on sources quoted in the javadoc of the relevant source files.

### FAQ
Q: Reflection is slow.
A: Most reflection stuff is done on initialisation only, so the impact is negligible. No, I haven't measured this ;)

### Copyright/trademarks
Websphere® is a registered trademark of IBM®.

### License
See COPYING

### Usage

Simply add this to your spring configuration.

   <cxf:bus>
     <cxf:outInterceptors>
       <bean class="com.xebia.opensource.cxf.extensions.WebsphereSslOutInterceptor" />
     </cxf:outInterceptors>
   </cxf:bus>
FROM websphere-liberty

#RUN echo "<server description=\"Default Server\"><keyStore id=\"defaultKeyStore\" location=\"/opt/ibm/java/jre/lib/security/cacerts\" password=\"changeit\" /></server>" > /opt/ibm/wlp/usr/servers/defaultServer/configDropins/defaults/keystore.xml

ADD databasesappdomaincloud.der /opt/ibm/wlp/usr/servers/defaultServer/

#RUN echo "yes" | $JAVA_HOME/bin/keytool -storepasswd -new password -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
#RUN echo "yes" | $JAVA_HOME/bin/keytool -import -alias my-root-ca -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
USER root
#RUN echo "yes" | $JAVA_HOME/bin/keytool -import -alias 541ce38f-5805-46be-8eb0-845939f2074b.974550db55eb4ec0983f023940bf637f.databases.appdomain.cloud -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit 
RUN echo "yes" | $JAVA_HOME/bin/keytool -import -trustcacerts -file /opt/ibm/wlp/usr/servers/defaultServer/databasesappdomaincloud.der -alias 541ce38f-5805-46be-8eb0-845939f2074b.974550db55eb4ec0983f023940bf637f.databases.appdomain.cloud -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit

ADD bootstrap.properties /opt/ibm/wlp/usr/servers/defaultServer/

ADD server.xml /opt/ibm/wlp/usr/servers/defaultServer/

ADD config.properties /opt/ibm/wlp/output/defaultServer/

ADD log4j.properties /opt/ibm/wlp/output/defaultServer/

RUN installUtility install --acceptLicense defaultServer

ADD es-poc.war /opt/ibm/wlp/usr/servers/defaultServer/apps/

EXPOSE 9080
EXPOSE 9443

ENV LICENSE accept



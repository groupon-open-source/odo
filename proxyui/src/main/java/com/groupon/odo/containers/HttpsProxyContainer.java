/*
 Copyright 2014 Groupon, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
package com.groupon.odo.containers;


import com.groupon.odo.proxylib.ClientService;
import com.groupon.odo.proxylib.Constants;
import com.groupon.odo.proxylib.Utils;
import com.groupon.odo.proxylib.models.Client;
import com.groupon.transparentproxy.TransparentProxy;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Configuration
public class HttpsProxyContainer extends GenericProxyContainer {
    @Bean
    public EmbeddedServletContainerFactory servletContainer() throws Exception {
        TomcatEmbeddedServletContainerFactory factory = new TomcatEmbeddedServletContainerFactory();
        final int httpsPort = Utils.GetSystemPort(Constants.SYS_HTTPS_PORT);
        factory.setPort(httpsPort);
        factory.setSessionTimeout(10, TimeUnit.MINUTES);
        factory.addContextCustomizers(new TomcatContextCustomizer() {
            @Override
            public void customize(Context context) {
                JarScanner jarScanner = new JarScanner() {
                    @Override
                    public void scan(ServletContext context, ClassLoader loader,
                                     JarScannerCallback scannerCallback, Set<String> args) {
                    }
                };
                context.setJarScanner(jarScanner);
            }
        });

        // extract keystores to temp file
        // the keystore needs to be in the filesystem and not just on the classpath
        // this ensures that it gets unpacked from the jar/war
        final File keyStore = com.groupon.odo.proxylib.Utils.copyResourceToLocalFile("tomcat.ks", "tomcat.ks");


        factory.addConnectorCustomizers(new TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                connector.setPort(httpsPort);
                connector.setSecure(true);
                Http11NioProtocol proto = (Http11NioProtocol) connector.getProtocolHandler();
                proto.setSSLEnabled(true);
                connector.setScheme("https");
                connector.setAttribute("keystorePass", "changeit");
                connector.setAttribute("keystoreFile", keyStore.getAbsolutePath());
                connector.setAttribute("clientAuth", "false");
                connector.setAttribute("sslProtocol", "TLS");
                connector.setAttribute("sslEnabled", true);
            }
        });

        try {
            TransparentProxy.getInstance();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        Connector[] connectors = createClientConnectors(keyStore.getAbsolutePath());
        if(connectors != null && connectors.length > 0) {
            factory.addAdditionalTomcatConnectors(connectors);
        }
        return factory;
    }

    private Connector[] createClientConnectors(String keyStorePath) {
        ArrayList<Connector> connectors = new ArrayList<Connector>();

        try {
            ArrayList<Client> clients = ClientService.getInstance().findAllClients();

            for (Client c : clients) {
                if (!c.getIsActive()) {
                    continue;
                }
                int port = c.getHttpsPort();
                if(port <= 0) {
                    continue;
                }

                Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
                Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();

                connector.setPort(port);
                connector.setSecure(true);
                protocol.setSSLEnabled(true);
                connector.setScheme("https");
                connector.setAttribute("keystorePass", "changeit");
                connector.setAttribute("keystoreFile", keyStorePath);
                connector.setAttribute("clientAuth", "false");
                connector.setAttribute("sslProtocol", "TLS");
                connector.setAttribute("sslEnabled", true);
                connectors.add(connector);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return connectors.toArray(new Connector[0]);
    }

}
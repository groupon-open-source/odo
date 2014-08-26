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
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Configuration
public class HttpProxyContainer extends GenericProxyContainer {
    @Bean
    public EmbeddedServletContainerFactory servletContainer() {
        TomcatEmbeddedServletContainerFactory factory = new TomcatEmbeddedServletContainerFactory();
        int httpPort = Utils.GetSystemPort(Constants.SYS_HTTP_PORT);
        factory.setPort(httpPort);
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

        Connector[] connectors = createClientConnectors();
            if(connectors != null && connectors.length > 0) {
                factory.addAdditionalTomcatConnectors(connectors);
            }
        return factory;
    }

    private Connector[] createClientConnectors() {
        ArrayList<Connector> connectors = new ArrayList<Connector>();

        try {
            ArrayList<Client> clients = ClientService.getInstance().findAllClients();

            for (Client c : clients) {
                if (!c.getIsActive()) {
                    continue;
                }
                int port = c.getHttpPort();
                if(port <= 0) {
                    continue;
                }

                Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
                Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();

                connector.setScheme("http");
                connector.setSecure(true);
                connector.setPort(port);
                protocol.setSSLEnabled(false);
                connectors.add(connector);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return connectors.toArray(new Connector[0]);
    }
}
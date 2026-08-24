package com.uniqa.crmpoc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
@EnableScheduling
public class CrmPocApplication {

    private static final Logger log = LoggerFactory.getLogger(CrmPocApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CrmPocApplication.class, args);
    }

    @EventListener(WebServerInitializedEvent.class)
    public void onWebServerReady(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String host = "localhost";
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ignored) {
            // fall back to "localhost"
        }
        log.info("---------------------------------------------------------");
        log.info("Application running! Access URLs:");
        log.info("Local:   http://localhost:{}", port);
        log.info("Network: http://{}:{}", host, port);
        log.info("---------------------------------------------------------");
    }
}

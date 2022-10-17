package com.syuez.rsocketclient;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class RSocketClientApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder()
                .main(RSocketClientApplication.class)
                .sources(RSocketClientApplication.class)
                .profiles("client")
                .run(args);
    }

}

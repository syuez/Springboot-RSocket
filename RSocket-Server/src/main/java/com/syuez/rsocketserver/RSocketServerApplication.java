package com.syuez.rsocketserver;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class RSocketServerApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder()
                .main(RSocketServerApplication.class)
                .sources(RSocketServerApplication.class)
                .profiles("Server")
                .run(args);
    }
}

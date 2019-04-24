package net.tonwu.tomcat.http;

import java.io.IOException;

import net.tonwu.tomcat.net.NioEndpoint;
// curl 10.1.59.237:10393 -X POST -d "user=abc&passwd=创"
public class TestHttpProcessor {
    public static void main(String[] args) {
        NioEndpoint endpoint = new NioEndpoint();
        endpoint.setProcessorClassName("net.tonwu.tomcat.http.HttpProcessor");
        try {
            endpoint.init();
            endpoint.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

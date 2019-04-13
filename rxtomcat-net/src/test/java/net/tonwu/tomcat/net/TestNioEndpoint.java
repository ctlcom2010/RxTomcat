package net.tonwu.tomcat.net;

import java.io.IOException;

import net.tonwu.tomcat.net.NioEndpoint;

public class TestNioEndpoint {

    public static void main(String[] args) {
        Acceptor.test = true;
        NioEndpoint endpoint = new NioEndpoint();
        endpoint.setProcessorClassName("net.tonwu.tomcat.net.EchoProcessor");
        try {
            endpoint.init();
            endpoint.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

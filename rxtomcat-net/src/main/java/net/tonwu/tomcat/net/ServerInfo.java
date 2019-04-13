package net.tonwu.tomcat.net;

import java.io.InputStream;
import java.util.Properties;

public class ServerInfo {

    public static String serverInfo = null;

    static {

        try {
            InputStream is = ServerInfo.class.getResourceAsStream("/net/tonwu/tomcat/util/ServerInfo.properties");
            Properties props = new Properties();
            props.load(is);
            is.close();
            serverInfo = props.getProperty("server.info");
        } catch (Throwable t) {
            ;
        }
        if (serverInfo == null)
            serverInfo = "Apache Tomcat";
    }

    public static void main(String args[]) {
        System.out.println("Server version: " + serverInfo);
        System.out.println("OS Name:        " + System.getProperty("os.name"));
        System.out.println("OS Version:     " + System.getProperty("os.version"));
        System.out.println("Architecture:   " + System.getProperty("os.arch"));
        System.out.println("JVM Version:    " + System.getProperty("java.runtime.version"));
        System.out.println("JVM Vendor:     " + System.getProperty("java.vm.vendor"));
    }

}

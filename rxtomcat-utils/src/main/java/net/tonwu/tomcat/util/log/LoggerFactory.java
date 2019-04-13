package net.tonwu.tomcat.util.log;

public class LoggerFactory {

    public static Log getLogger(Class<?> clazz) {
        return new LogImpl();
    }

}

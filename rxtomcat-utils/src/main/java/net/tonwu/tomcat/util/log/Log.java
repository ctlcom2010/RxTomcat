package net.tonwu.tomcat.util.log;

public interface Log {
    public void debug(Object message, Object... args);

    public void info(Object message, Object... args);

    public void error(Object message, Throwable t, Object... args);
}

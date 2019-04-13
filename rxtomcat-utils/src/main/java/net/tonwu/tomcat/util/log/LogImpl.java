package net.tonwu.tomcat.util.log;

public class LogImpl implements Log {
    public static final String TOKEN = "{}";

    @Override
    public void debug(Object message, Object... args) {
        System.out.println(log(message, args));
    }

    @Override
    public void info(Object message, Object... args) {
        System.out.println(log(message, args));
    }

    @Override
    public void error(Object message, Throwable t, Object... args) {
        System.out.println(log(message, args));
    }

    public static String log(Object message, Object... args) {
        String retv = String.valueOf(message);
        if (args != null && args.length > 0) {
            StringBuilder builder = new StringBuilder();
            int start = 0;
            for (Object arg : args) {
                int tokenIndex = retv.indexOf(TOKEN, start);
                if (tokenIndex < 0) {
                    builder.append(retv.substring(start));
                    builder.append(" ");
                    builder.append(arg);
                    start = retv.length();
                } else {
                    builder.append(retv.substring(start, tokenIndex));
                    builder.append(arg);
                    start = tokenIndex + TOKEN.length();
                }
            }
            builder.append(retv.substring(start));
            retv = builder.toString();
        }
        return retv;
    }
}

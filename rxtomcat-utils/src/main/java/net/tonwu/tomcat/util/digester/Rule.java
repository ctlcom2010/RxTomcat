package net.tonwu.tomcat.util.digester;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.xml.sax.Attributes;

public abstract class Rule {

    protected Digester digester = null;

    public Rule() {
    }

    public void begin(String uri, String qName, Attributes attributes) throws Exception {

    }

    public void end(String uri, String qName) throws Exception {

    }

    public void setDigester(Digester digester) {
        this.digester = digester;
    }

    // Reflection
    protected Object newInstance(String className) {
        try {
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
            return clazz.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    protected void invokeMethod(Object target, String methodName, Object... args) {
        Class<?>[] clazzArgs = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            clazzArgs[i] = args[i].getClass();
        }
        Method md = null;
        Method[] methods = target.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (methodName.equals(method.getName()) && (Arrays.equals(clazzArgs, method.getParameterTypes()))) {
                md = method;
            }
        }

        if (md != null) {
            try {
                md.invoke(target, args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected void invokeSetMethod(Object target, String methodName, String value) {
        Method targetMethod = null;
        Method[] methods = target.getClass().getMethods();
        for (Method method : methods) {
            if (methodName.equals(method.getName())) {
                targetMethod = method;
            }
        }
        try {
            String param = targetMethod.getParameterTypes()[0].getName();
            if ("java.lang.String".equals(param)) {
                targetMethod.invoke(target, value);
            } else if ("java.lang.Integer".equals(param) || "int".equals(param)) {
                targetMethod.invoke(target, Integer.valueOf(value));
            } else if ("java.lang.Boolean".equals(param) || "boolean".equals(param)) {
                targetMethod.invoke(target, Boolean.valueOf(value));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

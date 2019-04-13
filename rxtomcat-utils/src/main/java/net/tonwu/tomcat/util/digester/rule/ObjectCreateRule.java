package net.tonwu.tomcat.util.digester.rule;

import org.xml.sax.Attributes;

import net.tonwu.tomcat.util.digester.Rule;
import net.tonwu.tomcat.util.log.Log;
import net.tonwu.tomcat.util.log.LoggerFactory;

public class ObjectCreateRule extends Rule {
    final static Log log = LoggerFactory.getLogger(ObjectCreateRule.class);

    private String className;

    public ObjectCreateRule(String clazz) {
        className = clazz;
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {
        if (className == null) {
            className = attributes.getValue("className");
        }
        log.debug("[ObjectCreateRule] - {} New {}", digester.match(), className);

        // className == null 配置文件格式错误 TODO
        Object instance = newInstance(className);
        digester.push(instance);
    }

    @Override
    public void end(String namespace, String name) throws Exception {
        Object top = digester.pop();
        log.debug("[ObjectCreateRule] - {} Pop {}", digester.match(), top.getClass().getName());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ObjectCreateRule[");
        sb.append("className=");
        sb.append(className);
        sb.append("]");
        return (sb.toString());
    }
}

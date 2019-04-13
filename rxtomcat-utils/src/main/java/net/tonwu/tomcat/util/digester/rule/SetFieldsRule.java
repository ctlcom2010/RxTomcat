package net.tonwu.tomcat.util.digester.rule;

import org.xml.sax.Attributes;

import net.tonwu.tomcat.util.digester.Rule;
import net.tonwu.tomcat.util.log.Log;
import net.tonwu.tomcat.util.log.LoggerFactory;

public class SetFieldsRule extends Rule {
    final static Log log = LoggerFactory.getLogger(SetFieldsRule.class);

    @Override
    public void begin(String uri, String qName, Attributes attributes) throws Exception {
        Object top = digester.peek();
        log.debug("[SetFieldsRule] - {} Set {} fields", digester.match(), top.getClass().getName());

        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.getQName(i);
            String value = attributes.getValue(i);

            // setXXX
            char[] chs = name.toCharArray();
            chs[0] = Character.toUpperCase(chs[0]);
            StringBuilder setter = new StringBuilder("set");
            setter.append(chs);
            log.debug("[SetPropertiesRule] - {} Setting field '{}' to '{}'", digester.match(), name, value);

            invokeSetMethod(top, setter.toString(), value);
        }
    }

    @Override
    public String toString() {
        return "SetFieldsRule[]";
    }
}

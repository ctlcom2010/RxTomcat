package net.tonwu.tomcat.util.digester.rule;

import net.tonwu.tomcat.util.digester.Rule;
import net.tonwu.tomcat.util.log.Log;
import net.tonwu.tomcat.util.log.LoggerFactory;

public class SetObjectFieldRule extends Rule {
    final static Log log = LoggerFactory.getLogger(SetObjectFieldRule.class);

    private String methodName = null;

    public SetObjectFieldRule(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public void end(String namespace, String name) throws Exception {
        // Identify the objects to be used
        Object child = digester.pop();
        Object parent = digester.pop();

        log.debug("[SetObjectFieldRule] - {} Call {}.{}({})", digester.match(), parent.getClass().getName(), methodName,
                child);

        invokeMethod(parent, methodName, child);

        digester.push(parent);
        digester.push(child);
    }

    /**
     * Render a printable version of this Rule.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SetObjectFieldRule[");
        sb.append("methodName=");
        sb.append(methodName);
        sb.append(", paramType=");
        sb.append("]");
        return (sb.toString());
    }
}

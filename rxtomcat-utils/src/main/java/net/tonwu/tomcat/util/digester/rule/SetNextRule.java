/**
 * Copyright 2019 tonwu.net - 顿悟源码
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tonwu.tomcat.util.digester.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.util.digester.Rule;

/**
 * 设置类之间的组合关系，假设栈顶元素为 a，下一个元素为 b，则调用 b.setXX(a)，将 a 设置为 b 的成员
 * 
 * @author tonwu.net
 */
public class SetNextRule extends Rule {
    final static Logger log = LoggerFactory.getLogger(SetNextRule.class);
    
    protected String methodName = null;
    protected String paramType = null;
    
    public SetNextRule(String methodName) {
        this(methodName, null);
    }
    
    public SetNextRule(String methodName, String paramType) {
        this.methodName = methodName;
        this.paramType = paramType;
    }
    

    @Override
    public void end(String namespace, String name) throws Exception {
        Object child = digester.peek(0);
        Object parent = digester.peek(1);
        Object[] argsType = new Class[1];
        if (paramType != null) {
            argsType[0] = loadClass(paramType);
        } else {
            argsType[0] = child.getClass();
        }
        
        log.debug("[SetNextRule]{{}} Call {}.{}({})", digester.match(), parent.getClass().getName(), methodName, child);
        
        invokeMethod(parent, methodName, new Object[]{child}, argsType);
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

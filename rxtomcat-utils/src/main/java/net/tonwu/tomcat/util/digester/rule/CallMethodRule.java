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

import org.xml.sax.Attributes;

import net.tonwu.tomcat.util.digester.Rule;

/**
 * 对栈顶对象调用某个方法，可配置方法参数个数以及参数类型，默认参数个数为 1，参数的值使用元素节点的文本内容
 * 
 * @author tonwu.net
 */
public class CallMethodRule extends Rule {

    protected String methodName;
    protected int paramCount;
    protected Class<?> paramTypes[] = null;

    protected String bodyText = null;

    public CallMethodRule(String methodName, int paramCount) {
        this(methodName, paramCount, null);
    }

    public CallMethodRule(String methodName, int paramCount, Class<?>[] paramTypes) {
        this.methodName = methodName;
        this.paramCount = paramCount;
        if (paramTypes == null) {
            if (paramCount == 0) {
                this.paramTypes = new Class[] { String.class };
            } else {
                this.paramTypes = new Class[paramCount];
                for (int i = 0; i < this.paramTypes.length; i++) {
                    this.paramTypes[i] = String.class;
                }
            }
        } else {
            this.paramTypes = paramTypes;
            boolean error = paramCount == 0 ? paramTypes.length - 1 != 0 : paramCount != paramTypes.length;
            if (error) {
                throw new IllegalArgumentException("参数个数 paramCount 必须和 paramTypes 数组长度一致");
            }
        }
    }

    @Override
    public void begin(String uri, String qName, Attributes attributes) throws Exception {
        if (paramCount > 0) {
            Object parameters[] = new Object[paramCount];
            digester.push(parameters);
        }
    }

    @Override
    public void body(String namespace, String name, String text) throws Exception {
        bodyText = text.trim();
    }

    @Override
    public void end(String uri, String qName) throws Exception {
        Object parameters[] = null;
        if (paramCount > 0) {
            parameters = (Object[]) digester.pop();
        } else {
            if (bodyText == null || bodyText.length() == 0) return;
            parameters = new Object[1];
            parameters[0] = bodyText;
        }
        // 根据配置参数类型，进行适当的转换
        for (int i = 0; i < paramTypes.length; i++) {
            Object param = parameters[i];
            parameters[i] = convert((String)param, paramTypes[i].getName());
        }
        
        Object target = digester.peek();
        
        invokeMethod(target, methodName, parameters, paramTypes);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CallMethodRule[");
        sb.append("methodName=").append(methodName);
        sb.append(", paramCount=").append(paramCount);
        sb.append(", paramTypes={");
        if (paramTypes != null) {
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(paramTypes[i].getName());
            }
        }
        sb.append("}").append("]");
        return sb.toString();
    }
}

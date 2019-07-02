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
 * 与 CallMethodRule 对应，获取其配置的参数，既能获取某个属性值，也能使用文本内容
 * 
 * @author tonwu.net
 */
public class CallParamRule extends Rule {

    protected int paramIndex;
    protected String attributeName;
    protected String bodyText;
    
    public CallParamRule(int paramIndex) {
        this(paramIndex, null);
    }
    
    public CallParamRule(int paramIndex, String attributeName) {
        this.paramIndex = paramIndex;
        this.attributeName = attributeName;
    }

    @Override
    public void begin(String uri, String qName, Attributes attributes) throws Exception {
        if (attributeName != null) {
            Object param = attributes.getValue(attributeName);
            if (param != null) {
                Object parameters[] = (Object[]) digester.peek();
                parameters[paramIndex] = param;
            }
        }
    }
    
    @Override
    public void body(String namespace, String name, String text) throws Exception {
        if (attributeName == null) {
            bodyText = text.trim();
            Object parameters[] = (Object[]) digester.peek();
            parameters[paramIndex] = bodyText;
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CallParamRule[");
        sb.append("paramIndex=").append(paramIndex);
        sb.append(", attributeName=").append(attributeName);
        sb.append("]");
        return sb.toString();
    }
}

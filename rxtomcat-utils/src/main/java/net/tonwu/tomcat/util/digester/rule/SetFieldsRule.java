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
import org.xml.sax.Attributes;

import net.tonwu.tomcat.util.digester.Rule;

/**
 * 根据 xml 元素属性名，反射调用栈顶元素的 set 方法，设置对应的成员变量值
 * 
 * @author tonwu.net
 */
public class SetFieldsRule extends Rule {
    final static Logger log = LoggerFactory.getLogger(SetFieldsRule.class);
    
    @Override
    public void begin(String uri, String qName, Attributes attributes) throws Exception {
        Object top = digester.peek(); // 获取栈顶元素

        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.getQName(i);
            String value = attributes.getValue(i);

            // setXXX
            char[] chs = name.toCharArray();
            chs[0] = Character.toUpperCase(chs[0]);
            StringBuilder setter = new StringBuilder("set");
            setter.append(chs);
            
            log.debug("[SetPropertiesRule]{{}} Set {} fields", digester.match(), top.getClass().getName());
            
            // 反射调用 set 方法
            invokeSetMethod(top, setter.toString(), value);
        }
    }

    @Override
    public String toString() {
        return "SetFieldsRule[]";
    }
}

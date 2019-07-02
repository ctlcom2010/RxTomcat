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
 * 创建对象规则，根据配置的 className 新建对象并压入栈顶
 * 
 * @author tonwu.net
 */
public class ObjectCreateRule extends Rule {
    final static Logger log = LoggerFactory.getLogger(ObjectCreateRule.class);
    
    private String className; // 全限定类名

    public ObjectCreateRule(String clazz) {
        className = clazz;
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {
        if (className == null) {
            className = attributes.getValue("className");
        }
        
        // className == null 配置文件格式错误
        if (className == null) {
            throw new RuntimeException("No class name specified for " + namespace + " " + name);
        }
        
        log.debug("[ObjectCreateRule]{" + digester.match() + "}New " + className);
        
        Object instance = newInstance(className);
        digester.push(instance);
    }

    @Override
    public void end(String namespace, String name) throws Exception {
        digester.pop();
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

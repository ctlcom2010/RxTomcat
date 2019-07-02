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
package net.tonwu.tomcat.util.digester;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.xml.sax.Attributes;

/**
 * 解析过程中遇到 xml 元素时的处理规则
 * 
 * @author tonwu.net
 */
public abstract class Rule {
    
    protected Digester digester = null;

    public Rule() {
    }
    
    public void begin(String uri, String qName, Attributes attributes) throws Exception {
    }
    public void body(String namespace, String name, String text) throws Exception {
    }
    public void end(String uri, String qName) throws Exception {
    }

    public void setDigester(Digester digester) {
        this.digester = digester;
    }

    protected Class<?> loadClass(String className) throws ClassNotFoundException {
        ClassLoader loader = digester.getClassLoader();
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        
        Class<?> clazz = loader.loadClass(className);
        return clazz;
    }
    
    // Reflection - 反射调用
    protected Object newInstance(String className) {
        try {
            Class<?> clazz = loadClass(className);
            return clazz.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    protected void invokeMethod(Object target, String methodName, Object[] args, Object[] argsType) {
        Method md = null;
        Method[] methods = target.getClass().getMethods();
        for (Method method : methods) {
            if (methodName.equals(method.getName()) && (Arrays.equals(argsType, method.getParameterTypes()))) {
                md = method;
                break;
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
            Object param = convert(value, targetMethod.getParameterTypes()[0].getName());
            targetMethod.invoke(target, param);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    protected Object convert(String param, String paramTypeName) {
        Object retv = null;
        if ("java.lang.String".equals(paramTypeName)) {
            retv = param;
        } else if ("java.lang.Integer".equals(paramTypeName) || "int".equals(paramTypeName)) {
            retv = Integer.valueOf(param);
        } else if ("java.lang.Boolean".equals(paramTypeName) || "boolean".equals(paramTypeName)) {
            retv = Boolean.valueOf(param);
        }
        return retv;
    }
    
}

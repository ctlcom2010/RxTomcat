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

import java.util.ArrayList;

/**
 * 对栈顶对象多次调用某个方法，次数由 CallParamMultiRule 配置，这里
 * 主要用于 addServletMapping 和 addFilterMapping
 * 
 * @author tonwu.net
 */
public class CallMethodMultiRule extends CallMethodRule {

    final int multiParamIndex;
    
    public CallMethodMultiRule(String methodName, int paramCount, int multiParamIndex) {
        super(methodName, paramCount);
        this.multiParamIndex = multiParamIndex;
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
            super.end(uri, qName);
        }
        
        // 根据配置参数类型，进行适当的转换
        for (int i = 0; i < paramTypes.length; i++) {
            if (i != multiParamIndex) {
                Object param = parameters[i];
                parameters[i] = convert((String)param, paramTypes[i].getName());
            }
        }
        
        Object target = digester.peek();
        
        ArrayList<?> multiParams = (ArrayList<?>) parameters[multiParamIndex];
        
        for (int j = 0; j < multiParams.size(); j++) {
            Object param = multiParams.get(j);
            parameters[multiParamIndex] = convert((String)param, paramTypes[multiParamIndex].getName());
            invokeMethod(target, methodName, parameters, paramTypes);
        }
    }
}

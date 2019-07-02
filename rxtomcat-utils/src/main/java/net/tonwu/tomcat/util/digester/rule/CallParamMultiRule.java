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
 * 配置多次调用某个方法，重复使用的参数，主要用于 addServletMapping 和 addFilterMapping
 * 
 * @author tonwu.net
 */
public class CallParamMultiRule extends CallParamRule {
    public CallParamMultiRule(int paramIndex) {
        super(paramIndex);
    }
    
    @Override
    public void body(String namespace, String name, String text) throws Exception {
        if (attributeName == null) {
            bodyText = text.trim();
            Object parameters[] = (Object[]) digester.peek();
            
            @SuppressWarnings("unchecked")
            ArrayList<String> params = (ArrayList<String>) parameters[paramIndex];
            if (params == null) {
                params = new ArrayList<>();
                parameters[paramIndex] = params;
            }
            params.add(bodyText);
        }
    }
}

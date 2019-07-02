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
package net.tonwu.tomcat.http;

import java.io.IOException;

/**
 * 容器适配器，用于连接 Endponit 和 Container
 * 
 * @author tonwu.net
 */
public interface Adapter {
    
    /**
     * 处理请求，生成响应
     * 
     * @param request 底层原始请求对象
     * @param response 底层原始响应对象
     * @throws IOException
     */
    void service(RawRequest request, RawResponse response)  throws Exception;
}

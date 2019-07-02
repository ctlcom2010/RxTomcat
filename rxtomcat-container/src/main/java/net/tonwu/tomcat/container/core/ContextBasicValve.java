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
package net.tonwu.tomcat.container.core;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import net.tonwu.tomcat.container.Valve;
import net.tonwu.tomcat.container.servletx.Request;
import net.tonwu.tomcat.container.servletx.Response;

/**
 * Context 处理管道固定尾阀门
 * 
 * @author tonwu.net
 */
public class ContextBasicValve extends Valve {

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        String requestPath = request.getRequestURI();
        if ((requestPath. startsWith("/META-INF/", 0))
                || (requestPath.startsWith("/META-INF"))
                || (requestPath.startsWith("/WEB-INF/", 0))
                || (requestPath.equalsIgnoreCase("/WEB-INF"))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Context cxt = request.getContext();
        
        ClassLoader oldClassLodaer = Thread.currentThread().getContextClassLoader();
        
        // 将当前线程的上下文类加载器替换成 Web 应用类加载器  (这个切换类加载器的动作 Tomcat 是在 StandardHostValve 中做的)
        Thread.currentThread().setContextClassLoader(cxt.getLoader().getClassLoader());
        
        Wrapper wrapper = request.getWrapper();
        wrapper.getPipeline().handle(request, response);
        
        // 还原当前线程的上下文类加载器
        Thread.currentThread().setContextClassLoader(oldClassLodaer);
    }

}

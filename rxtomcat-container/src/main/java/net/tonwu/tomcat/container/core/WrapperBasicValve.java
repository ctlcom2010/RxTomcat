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

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.container.Valve;
import net.tonwu.tomcat.container.servletx.AppFilterChain;
import net.tonwu.tomcat.container.servletx.Request;
import net.tonwu.tomcat.container.servletx.Response;

/**
 * Wrapper 处理通道固定尾节点，加载 Servlet，创建 FilterChain，调用 Servlet.service 方法
 * 
 * @author tonwu.net
 */
public class WrapperBasicValve extends Valve {
    final static Logger log = LoggerFactory.getLogger(WrapperBasicValve.class);

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        Wrapper wrapper = request.getWrapper();

        Servlet servlet = null;
        
        try {
            servlet = wrapper.allocate();
        } catch (Throwable t) {
            log.error("Allocate exception for servlet [{" + wrapper.getName() + "}]", t);
            request.setAttribute("javax.servlet.exception", t);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        AppFilterChain filterChain = AppFilterChain.createFilterChain(request, wrapper, servlet);
        
        if (servlet != null && filterChain != null) {
            try {
                filterChain.doFilter(request, response);
            } catch (Throwable t) {
                log.error("Servlet.service() for servlet [{" + wrapper.getName() + "}] throw exception",t);
                request.setAttribute("javax.servlet.exception", t);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                filterChain.release();
            }
        }
    }
}

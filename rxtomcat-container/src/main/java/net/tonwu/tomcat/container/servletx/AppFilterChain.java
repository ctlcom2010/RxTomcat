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
package net.tonwu.tomcat.container.servletx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.container.core.Context;
import net.tonwu.tomcat.container.core.Wrapper;

/**
 * 实现 FilterChain 责任链模式
 * 
 * @author tonwu.net
 */
public class AppFilterChain implements FilterChain {
    final static Logger log = LoggerFactory.getLogger(AppFilterChain.class);
            
    private List<FilterWrapper> filters = new ArrayList<>();
    private int pos = 0;
    private int n = 0;

    private Servlet servlet;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        if (pos < n) {
            FilterWrapper wrapper = filters.get(pos++);
            try {
                Filter filter = wrapper.getFilter();
                filter.doFilter(request, response, this);
            } catch (Exception e) {
                throw new ServletException("Filter execution threw an exception", e);
            }
            return;
        }

        servlet.service(request, response);
    }

    public void addFilter(FilterWrapper filter) {
        filters.add(filter);
        n++;
    }

    public void setServlet(Servlet servlet) {
        this.servlet = servlet;
    }

    public void release() {
        filters.clear();
        servlet = null;
        pos = 0;
        n = 0;
    }

    /**
     * 根据请求映射的 Servlet 创建一个过滤器链
     * 
     * @param request 请求对象
     * @param wrapper Servlet 包装对象
     * @param servlet 匹配的 Servlet 对象
     * @return 一个 AppFilterChain 实例
     */
    public static AppFilterChain createFilterChain(ServletRequest request, Wrapper wrapper, Servlet servlet) {
        if (servlet == null) return null;
        
        AppFilterChain chain = new AppFilterChain();
        chain.setServlet(servlet);

        // filter
        Context context = (Context) wrapper.getParent();
        HashMap<String, FilterWrapper> filters = context.getFilters();
        if (filters == null || filters.size() == 0) {
            return chain;
        }

        String requestPath = wrapper.getWrapperPath();

        for (FilterWrapper filter : filters.values()) {
            if (!matchFiltersURL(filter, requestPath)) {
                continue;
            }

            chain.addFilter(filter);
        }
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("根据 Servlet 路径创建过滤器链\r\n======Filter Chain======");
            sb.append("\r\n  Request Path: ").append(wrapper.getWrapperPath());
            sb.append("\r\n  Filters : ").append(chain.getFilters());
            sb.append("\r\n========================");
            log.debug(sb.toString());
        }

        return chain;
    }
    
    private List<FilterWrapper> getFilters() {
        return filters;
    }

    /** 根据 uri 匹配 Filter */
    private static boolean matchFiltersURL(FilterWrapper filter, String requestPath) {
        if (filter.getMatchAllUrlPatterns()) {
            return true;
        }

        if (requestPath == null)
            return false;

        List<String> testPaths = filter.getUrlPatterns();
        for (String testPath : testPaths) {
            // Case 1 - Exact Match - 精确匹配
            if (testPath.equals(requestPath))
                return true;

            // Case 2 - Path Match ("/.../*") - 模糊路径匹配
            if (testPath.equals("/*"))
                return true;
            if (testPath.endsWith("/*")) {
                if (testPath.regionMatches(0, requestPath, 0, testPath.length() - 2)) {
                    if (requestPath.length() == (testPath.length() - 2)) {
                        return true;
                    } else if ('/' == requestPath.charAt(testPath.length() - 2)) {
                        return true;
                    }
                }
                return false;
            }

            // Case 3 - Extension Match - 后缀名匹配
            if (testPath.startsWith("*.")) {
                int slash = requestPath.lastIndexOf('/');
                int period = requestPath.lastIndexOf('.');
                if ((slash >= 0) && (period > slash) && (period != requestPath.length() - 1)
                        && ((requestPath.length() - period) == (testPath.length() - 1))) {
                    return (testPath.regionMatches(2, requestPath, period + 1, testPath.length() - 2));
                }
            }

            // Case 4 - "Default" Match
            return false; // NOTE - Not relevant for selecting filters
        }

        return false;
    }

}

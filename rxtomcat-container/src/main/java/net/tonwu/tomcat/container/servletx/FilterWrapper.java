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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.container.core.Context;
/**
 * 封装  Filter - class、name、urlPatterns
 * 
 * @author tonwu.net
 */
public class FilterWrapper implements FilterConfig {
    final Logger log = LoggerFactory.getLogger(FilterWrapper.class);
    
    private Context context = null;
    
    private transient Filter filter = null;
    
    private String filterClass = null;
    private String filterName = null;
    
    private List<String> urlPatterns = new ArrayList<>();
//    private List<String> servletNames = new ArrayList<>();
    
    private boolean matchAllUrlPatterns = false;
    
    public boolean getMatchAllUrlPatterns() {
        return matchAllUrlPatterns;
    }
    
    public void addURLPattern(String urlPattern) {
        if ("*".equals(urlPattern)) {
            this.matchAllUrlPatterns = true;
        } else {
            urlPatterns.add(urlPattern);
        }
    }
    /***
     * 获取对应的 Fitler 实例，可能会根据 filterClass 触发类加载
     */
    public Filter getFilter() throws ClassNotFoundException, InstantiationException,
                                IllegalAccessException, ServletException {
        if (filter != null) {
            return filter;
        }
        ClassLoader classLoader =  context.getLoader().getClassLoader();

        Class<?> clazz = classLoader.loadClass(filterClass);
        filter = (Filter) clazz.newInstance();
        filter.init(this);

        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public String getFilterClass() {
        return filterClass;
    }

    public void setFilterClass(String filterClass) {
        this.filterClass = filterClass;
    }

    public String getFilterName() {
        return filterName;
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    
    public void release() {
        if (filter != null) {
            log.debug("  Destroy filter [{}]", filterClass);
            filter.destroy();
            filter = null;
        }
    }
    
    public List<String> getUrlPatterns() {
        return urlPatterns;
    }

    @Override
    public String toString() {
        return filterName + "->" + urlPatterns;
    }

    @Override
    public ServletContext getServletContext() {
        return context.getServletContext();
    }
    @Override
    public String getInitParameter(String name) {
        return null;
    }
    @Override
    public Enumeration<?> getInitParameterNames() {
        return null;
    }
}

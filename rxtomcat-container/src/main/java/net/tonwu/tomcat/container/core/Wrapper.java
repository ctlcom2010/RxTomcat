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

import java.util.Enumeration;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.container.Container;
import net.tonwu.tomcat.container.Loader;

/**
 * 与 Servlet 一一对应，管理实现 Servlet 生命周期方法，加载、初始化和销毁
 * 
 * @author tonwu.net
 */
public class Wrapper extends Container implements ServletConfig {
    final static Logger log = LoggerFactory.getLogger(Wrapper.class);
    
    private volatile Servlet instance;
    private String servletClass;
    
    public Wrapper() {
        pipeline.setBasic(new WrapperBasicValve());
    }

    @Override
    public void addChild(Container child) {
        throw new IllegalStateException("Wrapper container may not have child containers");
    }

    public Servlet allocate() throws ServletException {
        if (instance == null) {
            instance = loadServlet();
        }
        return instance;
    }

    public Servlet loadServlet() throws ServletException {
        Servlet servlet = null;
        Class<?> clazz = null;

        Context appCxt = (Context) getParent();
        Loader loader = appCxt.getLoader();
        ClassLoader cl = loader.getClassLoader();
        
        try {
            if (cl != null) {
                clazz = cl.loadClass(servletClass);
            } else {
                clazz = Class.forName(servletClass);
            }

            servlet = (Servlet) clazz.newInstance();
            servlet.init(this);
        } catch (Exception e) {
            log.error("Error loading " + cl + " " + servletClass, e);
            throw new ServletException(e.getMessage());
        }
        return servlet;
    }

    @Override
    public void init() throws Exception {
    }

    @Override
    public void startInternal() {
    }
    
    @Override
    public void stop() throws Exception {
        if (instance != null) {
            log.debug("  Destroy servlet [{}]", servletClass);
            instance.destroy();
            instance = null;
        }
    }
    
    @Override
    public void backgroundProcess() {
        // Servlet 或者 jsp 重新加载
    }
    
    private String wrapperPath;
    
    // Getter & Setter
    public Servlet getInstance() {
        return instance;
    }

    public void setInstance(Servlet instance) {
        this.instance = instance;
    }

    public String getServletClass() {
        return servletClass;
    }

    public void setServletClass(String servletClass) {
        this.servletClass = servletClass;
    }

    public String getWrapperPath() {
        return wrapperPath;
    }
    public void setWrapperPath(String wrapperPath) {
        this.wrapperPath = wrapperPath;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("@").append(servletClass);
        return sb.toString();
    }

    // ServletConfig Methods
    @Override
    public String getServletName() {
        return name;
    }

    @Override
    public ServletContext getServletContext() {
        return ((Context)getParent()).getServletContext();
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

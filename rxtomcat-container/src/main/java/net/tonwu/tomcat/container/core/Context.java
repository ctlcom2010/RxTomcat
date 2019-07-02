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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.container.Container;
import net.tonwu.tomcat.container.Loader;
import net.tonwu.tomcat.container.servletx.AppContext;
import net.tonwu.tomcat.container.servletx.FilterWrapper;
import net.tonwu.tomcat.container.session.Manager;
/**
 * 应用程序在内部的表现类，包含 web.xml 配置的参数、Servlet 和 Filter
 * 
 * @author tonwu.net
 */
public class Context extends Container {
    final Logger log = LoggerFactory.getLogger(Context.class);
    
    private Connector connector;
    
    public static final String AppWebXml = "/WEB-INF/web.xml";
    public static final String  RESOURCES_ATTR = "app.resources";
    
    private String appBase = "webapp";
    private String welcomeFile = "index.html";
    
    /** 容器类加载器，从 rxtomcat.home/lib/ 目录加载类 */
    private ClassLoader parentClassLoader = Context.class.getClassLoader();
    
    private AppContext appContext;
    private WebResource resources;
    
    /** 应用正在热部署 */
    private volatile boolean paused = false;
    
    /** 是否是集群 */
    private boolean distributable = false;
    
    /** web 应用的名称 */
    private String docBase;
    private String docBasePath;
    
    /** 默认Servlet，/* */
    private Wrapper defaultWrapper;
    
    // web.xml 中的 Servlet & Mapping
    /** 
     * 精确匹配 - 完全匹配的 URL，比如 /catalog，URL 必须与它相等才能匹配
     */
    private TreeMap<String, Wrapper> exactWrappers = new TreeMap<>();
    
    /** 
     * 扩展名匹配 - 以 '*.' 为前缀的 URL，比如 *.bop，存储时的 key 是 bop，去除  *.
     */
    private TreeMap<String, Wrapper> extensionWrappers = new TreeMap<>();
    
    /** web.xml 中的 Filter */
    private HashMap<String, FilterWrapper> filters = new HashMap<>();
    
    private Loader loader;
    private Manager manager;
    
    /** context-param 配置的参数 */
    private final ConcurrentMap<String, String> parameters = new ConcurrentHashMap<>();
    
    private HashMap<String, String> mimeMappings = new HashMap<>();
    
    /**
     * 模糊匹配 以 - '/*' 结尾的 URL，比如 /foo/*，存储时的 key 是 /foo，去除 /*
     */
    private TreeMap<String, Wrapper> wildcardWrappers = new TreeMap<>();
    
    public Context() {
        pipeline.setBasic(new ContextBasicValve());
        // 添加一个声明周期监听者，处理部署
        addLifecycleListener(new ContextConfig());
    }
    
    /**
     * 从 web.xml 提取 filter-mapping
     * @param filterName 配置的 filter 名称
     * @param urlPattern 配置的 url 映射，支持多个
     */
    public void addFilterMapping(String filterName, String urlPattern) {
        FilterWrapper filterWrapper = filters.get(filterName);
        if (filterWrapper == null) {
            throw new IllegalArgumentException("unknown filter name");
        }
        filterWrapper.addURLPattern(urlPattern);
    }

    public void addFilterWrapper(FilterWrapper filter) {
        filter.setContext(this);
        filters.put(filter.getFilterName(), filter);
    }
    
    public String getParameter(String name) {
        return parameters.get(name);
    }
    public void addParameter(String name, String value) {
        // 验证上下文初始化参数
        if ((name == null) || (value == null)) {
            throw new IllegalArgumentException ("Both parameter name and parameter value are required");
        }

        // 如果尚未存在，将此参数添加到定义的集合中
        String oldValue = parameters.putIfAbsent(name, value);
        if (oldValue != null) { // 否则抛出重复异常
            throw new IllegalArgumentException("Duplicate context initialization parameter: " + name);
        }
    }
    /**
     * 从 web.xml 提取 servlet-mapping
     * 
     * @param servletName 配置的 servlet 名称
     * @param urlPattern 配置的要处理的 url
     */
    public void addServletMapping(String servletName, String urlPattern) {
        Wrapper servletWrapper = (Wrapper) findChild(servletName);
        if (servletWrapper == null) {
            throw new IllegalArgumentException("unknown servlet name");
        }
        String key = null;
        if (urlPattern.endsWith("/*")) {
            key = urlPattern.substring(0, urlPattern.length() - 2);
            wildcardWrappers.put(key, servletWrapper);
        } else if (urlPattern.startsWith("*.")) {
            key = urlPattern.substring(2);
            extensionWrappers.put(key, servletWrapper);
        } else if (urlPattern.equals("/")) {
            defaultWrapper = servletWrapper;
        } else {
            key = urlPattern;
            exactWrappers.put(key, servletWrapper);
        }
    }
    
    @Override
    public void backgroundProcess() throws Exception {
        if (loader != null) {
            loader.backgroundProcess();
        }

        if (manager != null) {
            manager.backgroundProcess();
        }
    }
    /** web 应用部署路径，默认是 webapp */
    public String getAppBase() {
        return appBase;
    }
    
    /** 部署的 web 应用所在的绝对路径，比如 /opt/rxtomcat/webapp/test */
    public String getDocBasePath() {
        if (docBasePath == null) {
            Path base = Paths.get(System.getProperty("rxtomcat.base"), appBase, docBase);
            docBasePath = base.toAbsolutePath().toString();
        }
        return docBasePath;
    }
    public void setDocBasePath(String basePath) {
        docBasePath = basePath;
    }
    
    /** 获取默认 servlet，处理静态资源 */
    public Wrapper getDefaultWrapper() {
        return defaultWrapper;
    }
    
    /** web 应用的名称 */
    public String getDocBase() {
        return docBase;
    }
    
    public TreeMap<String, Wrapper> getExactWrappers() {
        return exactWrappers;
    }
    public TreeMap<String, Wrapper> getExtensionWrappers() {
        return extensionWrappers;
    }

    public HashMap<String, FilterWrapper> getFilters() {
        return filters;
    }

    public Loader getLoader() {
        return loader;
    }

    public Manager getManager() {
        return manager;
    }
    
    /**
     * 获取文件真实的绝对路径
     * 
     * @param path 文件相对路径
     * @return 绝对路径，比如 index.html -> /opt/rxtomcat/webapp/test/index.html
     */
    public String getRealPath(String path) {
        File file = new File(getDocBasePath(), path);
        return file.getAbsolutePath();
    }

    public TreeMap<String, Wrapper> getWildcardWrappers() {
        return wildcardWrappers;
    }

    @Override
    public void init() throws Exception {
        defaultWrapper = new Wrapper();
        defaultWrapper.setName("default");
        defaultWrapper.setServletClass("net.tonwu.tomcat.container.servletx.DefaultServlet");
        addChild(defaultWrapper);
        
        resources = new WebResource(this);
        getServletContext().setAttribute(RESOURCES_ATTR, resources);
        
        
        mimeMappings.put("css","text/css");
        mimeMappings.put("exe","application/octet-stream");
        mimeMappings.put("gif","image/gif");
        mimeMappings.put("htm","text/html");
        mimeMappings.put("html","text/html");
        mimeMappings.put("ico","image/x-icon");
        mimeMappings.put("jpe","image/jpeg");
        mimeMappings.put("jpeg","image/jpeg");
        mimeMappings.put("jpg","image/jpeg");
        mimeMappings.put("js","application/javascript");
        mimeMappings.put("json","application/json");
        mimeMappings.put("png","image/png");
        mimeMappings.put("svg","image/svg+xml");
        mimeMappings.put("txt","text/plain");
        mimeMappings.put("xml","application/xml");
        
        fireLifecycleEvent(LifecycleEventType.INIT);
    }

    public boolean isDistributable() {
        return distributable;
    }

    public void setAppBase(String appBase) {
        this.appBase = appBase;
    }

    public void setDefaultWrapper(Wrapper defaultWrapper) {
        this.defaultWrapper = defaultWrapper;
    }

    public void setDistributable(boolean distributable) {
        this.distributable = distributable;
    }
    public void setDocBase(String docBase) {
        this.docBase = docBase;
        
    }
    public void setExactWrappers(TreeMap<String, Wrapper> exactWrappers) {
        this.exactWrappers = exactWrappers;
    }

    public void setExtensionWrappers(TreeMap<String, Wrapper> extensionWrappers) {
        this.extensionWrappers = extensionWrappers;
    }
    
    public void setFilters(HashMap<String, FilterWrapper> filters) {
        this.filters = filters;
    }
    
    public void setWildcardWrappers(TreeMap<String, Wrapper> wildcardWrappers) {
        this.wildcardWrappers = wildcardWrappers;
    }

    /**
     * 部署应用，从 web.xml 提取 Servlet 和 Filter
     */
    @Override
    public void startInternal() throws Exception {
        
        // 添加一个用于报告错误的 Valve
        pipeline.addValve(new ErrorReportValve());
        
        // Session 管理，Cluster TODO
        manager = new Manager();
        
        // 初始化 web 应用类加载器
        loader = new Loader(parentClassLoader, this);
        
        // 初始化并启动连接器
        connector = new Connector();
        connector.setContext(this);
        connector.start();
    }
    
    @Override
    public void stop() throws Exception {
        connector.stop();
        
        // destory filters
        for (FilterWrapper filterWrapper : filters.values()) {
            filterWrapper.release();
        }
        filters.clear();
    }

    public String getWelcomeFile() {
        return welcomeFile;
    }

    public void setWelcomeFile(String welcomeFile) {
        this.welcomeFile = welcomeFile;
    }

    public ServletContext getServletContext() {
        if (appContext == null) {
            appContext = new AppContext(this);
        }
        return appContext;
    }
    
    public String findMimeMapping(String extension) {
        return mimeMappings.get(extension);
    }

    public void setParentClassLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }
    
    public ClassLoader getConatinerClassLoader() {
        return parentClassLoader;
    }
    
    /** 应用是否正在热部署 */
    public boolean getPaused() {
        return paused;
    }
    public void setPaused(boolean value) {
        paused = value;
    }

    public void reload() throws Exception {
        log.debug("Context [{}] is reloading ...", docBase);
        setPaused(true);
        
        // 将成功加载的 Servlet 和 Filter 释放
        Container[] wrappers = findChildren();
        for (Container wrapper : wrappers) {
            try {
                ((Wrapper)wrapper).stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        for (FilterWrapper filter : filters.values()) {
            filter.release();
        }
        
        loader.stop();
        
        // 创建一个新的类加载器，这样旧的 Loader 加载的类会全部被卸载回收
        loader = new Loader(parentClassLoader, this);
        
        setPaused(false);
        log.debug(" Context [{}] reload is completed", docBase);
    }
    
    public WebResource webResources() {
        return resources;
    }
    
    @Override
    public void log(String msg) {
        log.info(msg);
    }
}

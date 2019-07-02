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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.TreeMap;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.container.servletx.Request;
import net.tonwu.tomcat.container.servletx.Response;
import net.tonwu.tomcat.http.Adapter;
import net.tonwu.tomcat.http.RawRequest;
import net.tonwu.tomcat.http.RawResponse;

/**
 * 适配容器，主要是映射 Servlet，尝试从 Cookie 中解析 Session ID
 * 
 * @author tonwu.net
 */
public class AdapterImpl implements Adapter {

    final static Logger log = LoggerFactory.getLogger(AdapterImpl.class);
    
    private Connector connector;

    public AdapterImpl(Connector connector) {
        this.connector = connector;
    }

    @Override
    public void service(RawRequest rawReq, RawResponse rawResp) throws Exception {
        // 创建并关联容器内部的请求和响应对象
        Request containerRequest = new Request();
        containerRequest.setRawReq(rawReq);
        Response containerResponse = new Response();
        containerResponse.setRawResp(rawResp);
        containerRequest.setResp(containerResponse);
        
        containerResponse.setContainerRequest(containerRequest);
        
        // 进入容器生成响应
        try {
            if (postParseRequest(rawReq, containerRequest, rawResp, containerResponse)) {
                connector.getContainer().getPipeline().handle(containerRequest, containerResponse);
            }
            containerResponse.finish();
        } finally {
            containerRequest.recycle();
            containerResponse.recycle();
        }
    }

    /**
     * 使用 url 映射 servlet；解析 sessionid
     */
    private boolean postParseRequest(RawRequest rawReq, Request req, RawResponse rawResp, Response resp) {
        Context context = connector.getContainer();

        // 严格来说要对 uri 规范化
        String uri = rawReq.getUri();
        try {
            uri = URLDecoder.decode(uri, rawReq.getEncoding().name());
        } catch (UnsupportedEncodingException e) {
        }
        
        // 匹配 Context
        if (uri.startsWith(context.getDocBase(), 1)) {
            req.setContext(context);
        } else {
            log.debug("匹配 Web 上下文对象 Context 失败，响应 404");
            rawResp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return false;
        }

        // uri 去除应用名称
        uri = uri.substring(uri.indexOf(context.getDocBase()) + context.getDocBase().length());
        // 没有 Servlet Path
        if ("".equals(uri)) {
            uri += "/";
        }

        boolean mapRequired = true;
        while (mapRequired) {
            Wrapper wrapper = mapServlet(context, uri);
            req.setWrapper(wrapper);

            // Parse session id in Cookie
            Cookie[] cookies = req.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("JSESSIONID".equalsIgnoreCase(cookie.getName())) {
                        String reqId = cookie.getValue();
                        req.setSessionId(reqId);
                    }
                }
            }
            
            if (log.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder(120);
                sb.append("映射 Servlet\r\n======Mapping Result======");
                sb.append("\r\n  Request Path: ").append(uri);
                sb.append("\r\n  Context: /").append(context.getDocBase());
                sb.append("\r\n  Wrapper: ").append(wrapper);
                sb.append("\r\n  jsessionid: ").append(req.getRequestedSessionId());
                sb.append("\r\n==========================");
                log.debug(sb.toString());
            }
            
            mapRequired = false;
            
            // Tomcat 在这里进行了多版本 Context 检测，由并行部署同一个 Web 应用导致不同的版本
            // 简单起见，这里只检测应用 class 文件是否正在热加载，没有实现 web.xml 检测和部署
            if (!mapRequired && context.getPaused()) {
                log.debug("Web 应用 [/{}] 正在热加载，重新映射 Servlet", context.getDocBase());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Should never happen
                }
                // reset mapping
                req.recycle();
                wrapper = null;
                mapRequired = true;
            }
        }
        return true;
    }
    
    /***
     * 映射 Servlet
     * 
     * @param context 请求匹配的应用上下文对象
     * @param uri 请求 Servlet 路径
     * @return 返回的肯定不为空，默认返回 DefaultServlet
     */
    private Wrapper mapServlet(Context context, String uri) {
        Wrapper mapWrapper = null;
        // Rule 1 -- Exact Match 精确匹配 /catalog
        TreeMap<String, Wrapper> exactWrappers = context.getExactWrappers();
        String key = exactWrappers.floorKey(uri);
        if (key != null && uri.equals(key)) {
            mapWrapper = exactWrappers.get(key);
        }
        // Rule 2 -- Prefix Match 模糊匹配 /foo/bar/*
        if (mapWrapper == null) {
            TreeMap<String, Wrapper> wildcardWrappers = context.getWildcardWrappers();
            key = wildcardWrappers.floorKey(uri);
            if (key != null) {
                // uri = /foo/bar, a/foo/bar, a/foo/bar/c, a/foo/bar/c/d
                // name = /foo/bar
                if (uri.startsWith(key) || uri.endsWith(key) || uri.contains(key + "/")) {
                    mapWrapper = wildcardWrappers.get(key);
                }
            }

        }

        // Rule 3 -- Extension Match 扩展名，最长路径的模糊匹配
        if (mapWrapper == null) {
            TreeMap<String, Wrapper> extensionWrappers = context.getExtensionWrappers();
            key = extensionWrappers.floorKey(uri);
            if (key != null && uri.endsWith("." + key)) {
                mapWrapper = extensionWrappers.get(key);
            }
        }

        // Rule 4 -- Welcome resources processing for servlets
        if (mapWrapper == null) {
            if (uri.endsWith("/")) {
                uri += context.getWelcomeFile();
            }
        }

        // Rule 5 -- Default servlet
        if (mapWrapper == null) {
            mapWrapper = context.getDefaultWrapper();
        }
        
        mapWrapper.setWrapperPath(uri);
        
        return mapWrapper;
    }
    
}

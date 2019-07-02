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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.tonwu.tomcat.container.core.Context;
import net.tonwu.tomcat.container.core.WebResource;
import net.tonwu.tomcat.container.core.WebResource.CachedResource;

/**
 * 默认 Servlet，用于处理静态资源，简单实现了缓存
 * 
 * @author tonwu.net
 */
public class DefaultServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /** 使用零拷贝发送的最小文件大小 48KB */
    protected int sendfileSize = 48 * 1024;
    protected boolean listings = false;
    
    protected transient WebResource resources = null;
    
    @Override
    public void init() throws ServletException {
        resources = (WebResource) getServletContext().getAttribute(Context.RESOURCES_ATTR);
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        
        CachedResource resource = resources.getResource(path);
        
        if (!resource.exists()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        
        if (resource.isDirectory()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        } else {
            // Checking If headers
            if (!checkIfHeaders(req, resp, resource)) {
                return;
            }
            
            // Find content type.
            String contentType = resource.getMimeType();
            if (contentType == null) {
                contentType = getServletContext().getMimeType(resource.getName());
                resource.setMimeType(contentType);
            }
            
            String eTag = resource.getETag();
            String lastModifiedHttp = resource.getLastModifiedHttp();
            
//            response.setHeader("Accept-Ranges", "bytes"); TODO
            
            // ETag header
            resp.setHeader("ETag", eTag);
            
            // Last-Modified header
            resp.setHeader("Last-Modified", lastModifiedHttp);
            
        }
        
        long contentLength = resource.getCachedContentLength();
        if (contentLength > 0) {
            resp.setContentType(resource.getMimeType());
            
            ServletOutputStream ostream = resp.getOutputStream();
            if (!checkSendfile()) {
                byte[] resourceBody = resource.getContent();
                
                if (resourceBody == null) {
                    InputStream is = resource.getInputStream();
                    InputStream istream = new BufferedInputStream(is, 2048);
                    byte buffer[] = new byte[2048];
                    int len = buffer.length;
                    while (true) {
                        try {
                            len = istream.read(buffer);
                            if (len == -1)
                                break;
                            ostream.write(buffer, 0, len);
                        } catch (IOException e) {
                            len = -1;
                            break;
                        }
                    }
                } else {
                    ostream.write(resourceBody);
                }
            }
        }
    }
    /**
     * 校验 if-match, if-none-match, if-modified-since, if-unmodified-since 请求头域值
     */
    private boolean checkIfHeaders(HttpServletRequest request, HttpServletResponse response, CachedResource resource) {
        String ifMatch = request.getHeader("if-match");
        if (ifMatch != null && ifMatch.indexOf('*') == -1) {
            boolean match = false;
            for (String etag : ifMatch.split(",")) {
                if (etag.trim().equals(resource.getETag())) {
                    match = true;
                }
            }
            if (!match) {
                response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
                return false;
            }
        }
        String ifNoneMatch = request.getHeader("if-none-match");
        if (ifNoneMatch != null) {
            boolean match = false;
            if (ifNoneMatch.equals("*")) {
                match = true;
            } else {
                for (String etag : ifNoneMatch.split(",")) {
                    if (etag.trim().equals(resource.getETag())) {
                        match = true;
                    }
                }
            }
            if (match) {
                if (("GET".equals(request.getMethod())) || ("HEAD".equals(request.getMethod()))) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", resource.getETag());
                } else {
                    response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
                }
                return false;
            }
        }
        long ifModifiedSince = request.getDateHeader("if-modified-since");
        if (ifModifiedSince != -1 && ifNoneMatch == null) {
            if (resource.getCachedLastModified() < (ifModifiedSince + 1000)) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                response.setHeader("ETag", resource.getETag());
                return false;
            }
        }
        long ifUnmodifiedSince = request.getDateHeader("if-unmodified-since");
        if (ifUnmodifiedSince != -1) {
            if (resource.getCachedLastModified() >= (ifUnmodifiedSince + 1000)) {
                response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
                return false;
            }
        }        
        return true;
    }

    private boolean checkSendfile() {
        return false;
    }
}

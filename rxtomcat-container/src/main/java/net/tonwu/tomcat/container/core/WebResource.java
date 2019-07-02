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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 主要用于缓存 web 应用程序静态资源
 * 
 * @author tonwu.net
 */
public class WebResource {
    
    /** 关联的应用上下文 */
    private Context context;
    
    public WebResource(Context context) {
        this.context = context;
    }

    private final ConcurrentMap<String, CachedResource> resourceCache = new ConcurrentHashMap<>();
    
    public CachedResource getResource(String path) {
        CachedResource resource = resourceCache.get(path);
        
        // 检查缓存是否过期以及检查缓存是否被修改
        if (resource != null && !resource.validateResource()) {
            resourceCache.remove(path);
            resource = null;
        }
        
        if (resource == null) {
            resource = new CachedResource(path);
            resourceCache.putIfAbsent(path, resource);
            
            resource.validateResource();
        }
        
        return resource;
    }
    /** 缓存资源对象 */
    public class CachedResource {
        private File resource = null;
        private String path; // 资源相对路径
        
        private long ttl = 5000; // 5s
        public long nextCheck;
        
        private long cachedLastModified = -1L;
        private long cachedContentLength = -1L;
        
        private byte[] cachedContent;
        
        private String mimeType;
        private String weakETag;
        
        public CachedResource(String path) {
            this.path = path;
        }
        
        public final String getETag() {
            if (weakETag == null) {
                synchronized (this) {
                    if (weakETag == null) {
                        long contentLength = getCachedContentLength();
                        long lastModified = getCachedLastModified();
                        if ((contentLength >= 0) || (lastModified >= 0)) {
                            weakETag = "w/\"" + contentLength + "-" +
                                       lastModified + "\"";
                        }
                    }
                }
            }
            return weakETag;
        }
        /**
         * 检查缓存是否过期以及检查缓存是否被修改
         * 
         * @return false 资源已过期且被修改，否则 true 有效资源 
         */
        protected boolean validateResource() {
            long now = System.currentTimeMillis();
            if (resource == null) {
                resource = new File(context.getRealPath(path));
                cachedLastModified = resource.lastModified();
                cachedContentLength = resource.length();
                cachedContent = cacheLoad();
            } else {
                // 缓存已过期
                if (now > nextCheck) {
                    // 且被修改
                    if (resource.lastModified() != getCachedLastModified()
                            || resource.length() != getCachedContentLength()) {
                        return false;
                    }
                } 
            }
            nextCheck = ttl + now;
            return true;
        }
        /** 小于 512KB 的静态资源缓存其字节数组 */
        private byte[] cacheLoad() {
            if (cachedContentLength > 0 && cachedContentLength < 512 * 1024) {
                int size = (int) cachedContentLength;
                byte[] result = new byte[size];

                int pos = 0;
                try (InputStream is = new FileInputStream(resource)) {
                    while (pos < size) {
                        int n = is.read(result, pos, size - pos);
                        if (n < 0) {
                            break;
                        }
                        pos += n;
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    return null;
                }
                return result;
            }
            return null;
        }

        public long getCachedContentLength() {
            return cachedContentLength;
        }
        
        public long getCachedLastModified() {
            return cachedLastModified;
        }
        
        public final String getLastModifiedHttp() {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            return sdf.format(new Date(getCachedLastModified()));
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public boolean exists() {
            return resource.exists();
        }

        public boolean isFile() {
            return resource.isFile();
        }

        public String getName() {
            return resource.getName();
        }

        public boolean isDirectory() {
            return resource.isDirectory();
        }

        public byte[] getContent() {
            return cachedContent;
        }
        
        /** 大小超过 512KB 时，使用 InputStream 底层流读取 */
        public InputStream getInputStream(){
            if (cachedContent != null) {
                return new ByteArrayInputStream(cachedContent);
            } else {
                try {
                    return new FileInputStream(resource);
                } catch (FileNotFoundException fnfe) {
                    return null;
                }
            }
        }
    }
}

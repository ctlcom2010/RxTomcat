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
package net.tonwu.tomcat.container;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.container.core.Context;

/**
 * web 应用类加载器，主要从 /WEB-INF/classes 和 /WEB-INF/lib 中加载类
 * 
 * @author tonwu.net
 */
public class Loader extends URLClassLoader {
    final static Logger log = LoggerFactory.getLogger(Loader.class);
    
    /** 关联的 web 应用 */
    private Context context;
    /** 内部 class 缓存容器 */
    protected final Map<String, ResourceEntry> resources = new ConcurrentHashMap<>();

    /** 先委托父加载器（Bootstrap ClassLoader）加载，然后再在 CLASSPATH 路径下加载 */
    private ClassLoader javaseClassLoader;
    
    private String classesPath;
    private String libraryPath;
    
    public Loader(ClassLoader parent, Context cxt) throws Exception {
        super(new URL[0], parent);
        context = cxt;
        this.javaseClassLoader = getSystemClassLoader();
        
        init();
    }

    /**
     * 检查类是否变动，是否要重新加载，这里实现的就是所谓的热加载
     */
    public void backgroundProcess() {
        boolean modified = false;
        
        // 检查已加载的 class 文件是否变动
        for (Entry<String,ResourceEntry> entry : resources.entrySet()) {
            if ( entry.getValue().type == 1) {
                long cachedLastModified = entry.getValue().lastModified;
                long lastModified = new File(classesPath,entry.getKey()).lastModified();
                if (lastModified != cachedLastModified) {
                    log.debug("Resource [{}] has been modified. The last modified time was [{}] and is now [{}]",
                            entry.getKey(),
                            cachedLastModified,
                            lastModified);
                    modified = true;
                    break;
                }
            }
        }
        
        // 检查是否有 jar 添加或删除
        // TODO
        
        if (modified) {
            try {
                context.reload();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public void stop() {
        resources.clear();
    }
    
    /** 添加 lib/*.jar 到自己的仓库 */
    public void init() throws Exception {
        
        classesPath = context.getRealPath("/WEB-INF/classes/");
        libraryPath = context.getRealPath("/WEB-INF/lib/");
        
        File webInfLib = new File(libraryPath);
        if (webInfLib.exists()) {
            addURL(new URL(webInfLib.toURI().toString()));

            String filenames[] = webInfLib.list();
            if (filenames != null) {
                for (int j = 0; j < filenames.length; j++) {
                    String filename = filenames[j].toLowerCase(Locale.ENGLISH);
                    if (!filename.endsWith(".jar"))
                        continue;
                    File file = new File(webInfLib, filenames[j]);
                    file = file.getCanonicalFile();
                    URL url = new URL(file.toURI().toString());
                    addURL(url);
                }
            }
        }
    }
    
    public ClassLoader getClassLoader() {
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }
    
    /**
     * 打破标准的双亲类加载器模型，按以下步骤加载类：<br>
     * <p>
     * 0. 检查本地缓存是否已经加载 <br>
     * 1. 检查是否已经被虚拟机加载 <br>
     * 2. 尝试使用系统类加载器加载，防止覆盖 rt.jar 核心类库 <br>
     * 3. 从本地文件系统读取 class 文件，并定义一个类 <br>
     * 4. 此时加载失败，尝试使用父类加载器加载 <br>
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz = null;
        
        // 0. 检查本地缓存是否已经加载
        clazz = findLoadedClassLocal(nameToPath(name));
        if (clazz != null) {
            if (resolve) resolveClass(clazz);
            return clazz;
        }
        
        // 1. 检查是否已经被虚拟机加载
        clazz = findLoadedClass(name);
        if (clazz != null) {
            if (resolve) resolveClass(clazz);
            return clazz;
        }
        // 2. 尝试使用系统类加载器加载，防止覆盖 rt.jar 核心类库
        try {
            clazz = javaseClassLoader.loadClass(name);
            if (clazz != null) {
                if (resolve) resolveClass(clazz);
                return clazz;
            }
        } catch (ClassNotFoundException Ignore) { }
        
        
        // 3. 从本地文件系统(classes/lib)中读取 class 文件，并定义一个类
        try {
            clazz = findClass(name);
            if (clazz != null) {
                if (resolve) resolveClass(clazz);
                return clazz;
            }
        } catch (ClassNotFoundException Ignore) { }
        
        // 4. 此时加载失败，尝试使用父类加载器加载
        if (clazz == null) {
            try {
                clazz = getParent().loadClass(name);
                if (clazz != null) {
                    if (resolve) resolveClass(clazz);
                    return clazz;
                }
            } catch (ClassNotFoundException Ignore) { }
        }
        
        // 异常
        throw new ClassNotFoundException(name);
    }
    
    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = null;
        try {
            // 首先从 WEB-INF/classes 下尝试加载
            clazz = findClassInternal(name);
        } catch (RuntimeException ignore) { }

        // 然后再从 WEB-INF/lib/*.jar 中加载
        if (clazz == null) {
            clazz = super.findClass(name);
        }
        
        return clazz;
    }
    
    /**
     * 首先查找缓存是否已经加载过，否则读取 class 文件字节，定义一个类
     * 
     * @param name 全限定类名，比如 net.tonwu.tomcat.container.Loader
     * @return 一个 Class 实例
     */
    protected Class<?> findClassInternal(String name) {
        ResourceEntry resource = resources.get(name);
        Class<?> clazz = null;
        
        if (resource == null) {
            String path = nameToPath(name);
            // 获取绝对路径
            Path classFile = Paths.get(classesPath, path);
            if (Files.exists(classFile)) {
                
                resource = new ResourceEntry();
                resource.type = 1;
                resources.put(path, resource);
                
                try (InputStream is = Files.newInputStream(Paths.get(classesPath, path))) {
                    byte[] content = new byte[is.available()];
                    is.read(content);
                    
                    clazz = defineClass(name, content, 0, content.length);
                    
                    resource.lastModified = classFile.toFile().lastModified();
                    resource.loadedClass = clazz;
                    
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            clazz = resource.loadedClass;
        }
        return clazz;
    }
    
    /** 查找本地缓存的已加载的类 */
    private Class<?> findLoadedClassLocal(String path) {
        ResourceEntry entry = resources.get(path);
        if (entry != null) {
            return entry.loadedClass;
        }
        return null;
    }
    
    @Override
    public URL getResource(String name) {
        // 首先从本地仓库查找
        URL url = findResource(name);
        
        // 在从父类加载器仓库查找
        if (url == null) {
            url = getParent().getResource(name);
        }
        return url;
    }

    @Override
    public URL findResource(final String name) {
        URL url = null;
        // 首先从 WEB-INF/classes 目录下查找资源
        ResourceEntry resource = resources.get(name);
        if (resource == null) {
            
            Path path = Paths.get(classesPath, name);
            if (Files.exists(path)) {
                resource = new ResourceEntry();
                resources.put(name, resource);
                try {
                    url = path.toUri().toURL();
                    resource.source = url;
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        } else {
            url = resource.source;
        }
        
        // 然后再在配置的仓库中查找
        if (url == null) {
            url = super.findResource(name);
        }
        
        return url;
    }
    
    private String nameToPath(String name) {
        return name.replace('.', '/').concat(".class");
    }
    
    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream stream = null;
        URL url = findResource(name);
        if (url != null) {
            try {
                stream = url.openStream();
            } catch (IOException ignore) { }
        }
        
        if (stream != null) {
            return stream;
        }
        
        stream = getParent().getResourceAsStream(name);
        return stream;
    }

    /**
     * 缓存的已加载类资源
     * 
     * @author tonwu.net
     */
    public static class ResourceEntry {
        /** 0=其他资源，1=从 WEB-INF/classes 加载的资源 */
        public int type = 0; 
        /** The "last modified" time of class file. 用于检查热加载 */
        public long lastModified = -1;

        /** Loaded class */
        public volatile Class<?> loadedClass = null;
        
        public URL source;
        
        public byte[] binaryContent = null;
    }
    
    public void setContext(Context context) {
        this.context = context;
    }
    
    @Override
    public String toString() {

        StringBuffer sb = new StringBuffer("WebappClassLoader\r\n");
        sb.append("  context: /");
        sb.append(context.getDocBase());
        sb.append("\r\n");
        sb.append("  repositories:\r\n");
        URL[] repositories = getURLs();
        if (repositories != null) {
            for (int i = 0; i < repositories.length; i++) {
                sb.append("    ");
                sb.append(repositories[i]);
                sb.append("\r\n");
            }
        }
        
        sb.append("----------> Parent Classloader:\r\n");
        sb.append(getParent().toString());
        sb.append("\r\n");
        return (sb.toString());

    }
    
}

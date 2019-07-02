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
package net.tonwu.tomcat.startup;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 启动类，main 方法所在，主要初始化工作路径和一个加载 lib/ 和 lib/*.jar 的类加载器，启动整个服务
 * <p>
 * 值得注意的是构建发布版本时，是分开打包的，所以这里是不能识别 Context 等任何其他模块的类，只能使用反射调用它的方法
 * 
 * @author tonwu.net
 */
public class Bootstrap {
    static {
        // Will always be non-null
        String userDir = System.getProperty("user.dir");

        // Home first
        String home = System.getProperty("rxtomcat.base");
        File homeFile = null;

        if (home != null) {
            File f = new File(home);
            try {
                homeFile = f.getCanonicalFile();
            } catch (IOException ioe) {
                homeFile = f.getAbsoluteFile();
            }
        }

        if (homeFile == null) {
            // Fall-back. Use current directory
            File f = new File(userDir);
            try {
                homeFile = f.getCanonicalFile();
            } catch (IOException ioe) {
                homeFile = f.getAbsoluteFile();
            }
        }
        System.setProperty("rxtomcat.base", homeFile.getPath());
    }

    /**
     * Daemon reference.
     */
    private Object contextDaemon = null;

    private ClassLoader commonLoader = null;

    private void init() throws Exception {
        commonLoader = createClassLoader(null);
        
        // 设置当前线程上下文类加载器为 commonLoader，后续的资源或者 class 基本上都在 ${rxtomcat.base}/lib 下 
        Thread.currentThread().setContextClassLoader(commonLoader);
        
        Class<?> clazz = commonLoader.loadClass("net.tonwu.tomcat.container.core.Context");
        contextDaemon = clazz.newInstance();
        String methodName = "setParentClassLoader";
        Class<?>[] paramTypes = new Class[] { Class.forName("java.lang.ClassLoader") };
        Method md = contextDaemon.getClass().getMethod(methodName, paramTypes);
        md.invoke(contextDaemon, commonLoader);

        Method method = contextDaemon.getClass().getMethod("init", (Class[]) null);
        method.invoke(contextDaemon, (Object[]) null);
    }

    /**
     * 创建一个 URLClassLoader，默认加载路径是 rxtomcat.base/lib 以及 rxtomcat.base/lib/*.jar
     * 
     * @param parent
     *            父类加载器
     * @return 返回一个 URLClassLoader 实例
     * @throws Exception
     *             MalformedURLException, IOException
     */
    private ClassLoader createClassLoader(ClassLoader parent) throws Exception {
        Set<URL> set = new LinkedHashSet<>();
        File libDir = new File(System.getProperty("rxtomcat.base"), "lib");
        if (libDir.exists()) {
            set.add(new URL(libDir.toURI().toString()));

            String filenames[] = libDir.list();
            if (filenames != null) {
                for (int j = 0; j < filenames.length; j++) {
                    String filename = filenames[j].toLowerCase(Locale.ENGLISH);
                    if (!filename.endsWith(".jar"))
                        continue;
                    File file = new File(libDir, filenames[j]);
                    file = file.getCanonicalFile();
                    URL url = new URL(file.toURI().toString());
                    set.add(url);
                }
            }
        }
        File log = new File(System.getProperty("rxtomcat.base"), "logback.xml");
        set.add(new URL(log.toURI().toString()));

        final URL[] array = set.toArray(new URL[set.size()]);
        if (parent != null) {
            return new URLClassLoader(array, parent);
        } else {
            return new URLClassLoader(array);
        }
    }

    public void start() throws Exception {
        long start = System.nanoTime();
        init();

        Method method = contextDaemon.getClass().getMethod("start", (Class[]) null);
        method.invoke(contextDaemon, (Object[]) null);

        StringBuilder sb = new StringBuilder();
        sb.append("Server startup in ").append((System.nanoTime() - start) / 1000000).append(" ms");

        String methodName = "log";
        Class<?>[] paramTypes = new Class[] { Class.forName("java.lang.String") };
        Method md = contextDaemon.getClass().getMethod(methodName, paramTypes);
        md.invoke(contextDaemon, sb.toString());

        Runtime.getRuntime().addShutdownHook(new Thread("shutdown-hook") {
            
            @Override
            public void run() {
                try {
                    Bootstrap.this.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // await();
        // stop();
    }

    public void stop() throws Exception {
        Method method = contextDaemon.getClass().getMethod("stop", (Class[]) null);
        method.invoke(contextDaemon, (Object[]) null);
    }

    public void await() {
    }

    public static void main(String[] args) throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.start();
    }
}

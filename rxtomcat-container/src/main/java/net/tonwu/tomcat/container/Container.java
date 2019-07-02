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

import java.util.HashMap;

/**
 * 容器抽象类，模拟实现的只有两个容器子类，分别是：<br>
 * <p>
 *  - Context - 表示一个 web 应用，这里也只支持一个应用 <br>
 *  - Wrapper - 表示一个 Servlet，负责它的加载、初始化和销毁<br>
 * <p>
 * 可嵌套的组件主要实现以下几个：<br>
 * <p>
 *   - Pipeline - 结合 Valve 提供管道处理模型<br>
 *   - Loader - Web 应用类加载器<br>
 *   - Manager -管理 Session<br>
 * <p>
 * 每个容器都会启动一个后台线程，周期性的做一些工作，Context 会检查类是否要热
 * 加载，Session 是否过期；Wrapper 检查是否要重新加载 Servlet
 * 
 * @author tonwu.net
 */
public abstract class Container extends Lifecycle {
    
    /** 容器名称 */
    protected String name;
    
    /** 父容器 */
    protected Container parent;
    
    /** 子容器 */
    protected final HashMap<String, Container> children = new HashMap<>();
    
    /** 处理管道 */
    protected Pipeline pipeline = new Pipeline(this);
    
    /** 后台处理线程  */
    private Thread thread = null;
    /** 后台线程是否执行完毕 */
    protected volatile boolean threadDone = false;
    /** 后台线程执行间隔，单位秒 */
    private int backgroundProcessorDelay = 5; // 单位-秒
    
    @Override
    public void start() throws Exception {
        fireLifecycleEvent(LifecycleEventType.START);
        
        startInternal();
        
        Container children[] = findChildren();
        for (int i = 0; i < children.length; i++) {
            if (children[i] instanceof Lifecycle)
                ((Lifecycle) children[i]).start();
        }
        
        // 初始化并启动后台处理线程
        if (thread == null && backgroundProcessorDelay > 0) {
            threadDone = false;
            String threadName = "Container-daemon[" + getName() + "]";
            thread = new Thread(new ContainerBackgroundProcessor(), threadName);
            thread.setDaemon(true);
            thread.start();
        }
    }
    
    /**
     * 抽象方法，由子类实现，容器后台线程会周期性调用此方法
     */
    public abstract void backgroundProcess() throws Exception;
    
    /**
     * 抽象方法，组件启动子类实现的方法
     */
    public abstract void startInternal() throws Exception;
    
    /**
     * 容器后台周期性线程执行单元
     */
    protected class ContainerBackgroundProcessor implements Runnable {
        @Override
        public void run() {
            while (!threadDone) {
                try {
                    backgroundProcess();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                try {
                    Thread.sleep(backgroundProcessorDelay * 1000L);
                } catch (InterruptedException Ignore) { }
            }
        }
    }
    
    /** 添加一个子容器 */
    public void addChild(Container child) {
        child.setParent(this);
        try {
            child.start();
        } catch (Exception ignore) { }
        
        children.put(child.name, child);
    }
    /** 根据容器名称查找一个子容器 */
    public Container findChild(String name) {
        return children.get(name);
    }
    public void removeChild(String name) {
        children.remove(name);
    }
    public Container[] findChildren() {
        Container results[] = new Container[children.size()];
        return children.values().toArray(results);
    }
    /** 获取容器名称 */
    public String getName() {
        return name;
    }
    /** 设置容器名称 */
    public void setName(String name) {
        this.name = name;
    }
    /** 获取父容器 */
    public Container getParent() {
        return parent;
    }
    /** 设置父容器 */
    public void setParent(Container parent) {
        this.parent = parent;
    }
    /** 在处理管道上添加一个阀门 Valve */
    public void addValve(Valve valve) {
        pipeline.addValve(valve);
    }
    /** 获取处理管道 */
    public Pipeline getPipeline() {
        return pipeline;
    }
    
    public void log(String msg) {
    }
}

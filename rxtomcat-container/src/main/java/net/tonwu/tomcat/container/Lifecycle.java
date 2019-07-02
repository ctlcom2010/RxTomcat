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

import java.util.EventObject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 生命周期抽象类，为组件的初始化、启动和停止提供统一的机制
 * 
 * @author tonwu.net
 */
public abstract class Lifecycle {
    
    /** 观察者模式，用于通知事件而注册的监听器列表 */
    private final List<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    
    /** 启动前的初始化工作， 初始完毕会触发 INIT 事件*/
    public abstract void init() throws Exception;
    
    /** 启动组件， 并触发 START 事件*/
    public abstract void start() throws Exception;
    
    /** 停止组件， 并触发 STOP 事件*/
    public abstract void stop() throws Exception;
    
    /** 添加一个观察者 */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycleListeners.add(listener);
    }
    /** 移除一个观察者 */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }
    /**
     * 触发一个声明周期事件
     * @param type
     * @throws Exception 
     */
    protected void fireLifecycleEvent(LifecycleEventType type) throws Exception {
        fireLifecycleEvent(type, null);
    }
    protected void fireLifecycleEvent(LifecycleEventType type, Object data) throws Exception {
        LifecycleEvent event = new LifecycleEvent(this, type, data);
        for (LifecycleListener listener : lifecycleListeners) {
            listener.lifecycleEvent(event);
        }
    }
    /** 事件类型 */
    public static enum LifecycleEventType {
        /** 初始化事件 */
        INIT,
        /** 组件启动事件 */
        START,
        /** 组件停止事件 */
        STOP
    }
    /** 生命周期监听器接口 */
    public static interface LifecycleListener {
        /** 处理对应的生命周期事件 */
        public void lifecycleEvent(LifecycleEvent event) throws Exception;
    }
    /** 生命周期事件封装的对象 */
    public static final class LifecycleEvent extends EventObject {
        private static final long serialVersionUID = 1L;
        private final Object data;
        private final LifecycleEventType type;
        
        public LifecycleEvent(Lifecycle lifecycle, LifecycleEventType type, Object data) {
            super(lifecycle);
            this.type = type;
            this.data = data;
        }

        public Object getData() {
            return data;
        }
        public LifecycleEventType getType() {
            return type;
        }
        public Lifecycle getLifecycle() {
            return (Lifecycle) getSource();
        }
    }
}

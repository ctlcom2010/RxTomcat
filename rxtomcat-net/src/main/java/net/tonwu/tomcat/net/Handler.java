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
package net.tonwu.tomcat.net;

import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 管理着通道和处理器的映射
 * 
 * @author tonwu.net
 */
public abstract class Handler {
    final Logger log = LoggerFactory.getLogger(Handler.class);
    
    /** 连接和处理器的映射，主要是非阻塞读或写不完整时，再次处理时关联旧的处理器 */
    private final Map<NioChannel, Processor> connections = new ConcurrentHashMap<>();

    /** 连接处理过程中 socket 可能的状态 */
    public enum SocketState {
        /** 长连接 */
        OPEN,
        /** 继续读取 */
        LONG,
        /** 发送 */
        WRITE,
        /** 断开连接 */
        CLOSED
    }
    /** 处理连接、通道 */
    public SocketState handle(NioChannel socket) {
        // 是否存在关联的 processor
        Processor processor = connections.get(socket);
        if (processor == null) {
            // 创建一个 Processor 对象，并与此 socket 关联
            processor = createProcessor();
            connections.put(socket, processor);
            log.debug("为通道 [{}] 创建新的 Processor [{}]", socket, processor);
        } else {
            log.debug("获取通道 [{}] 已创建关联的 Processor [{}]", socket, processor);
        }

        SocketState state = SocketState.CLOSED;
        // 调用 Processor 处理
        state = processor.process(socket);
        // 检查处理结果
        if (state == SocketState.LONG) {
            log.debug("[请求头数据不完整]，通道 [{}] 重新声明关注 [读取] 事件", socket);
            // 处理期间发现读取的数据不完整，要再次读取，此时通道要再次在 Poller 上声明关注读取事件
            socket.getPoller().register(socket, SelectionKey.OP_READ);
            // 不会移除通道和处理器的映射关系
        } else if (state == SocketState.OPEN) {
            log.debug("[保持连接]，通道 [{}] 重新声明关注 [读取] 事件", socket);
            // 长连接，要保持连接，因为不知道下次请求的时间，所以可以回收利用此通道关联的 Processor
            // 这里主要是模拟实现，并没有真正实现 Processor 对象池
            connections.remove(socket);
            // 再次声明关注读取事件
            socket.getPoller().register(socket, SelectionKey.OP_READ);
        } else if (state == SocketState.WRITE) {
            log.debug("[写入响应数据]，通道 [{}] 声明关注 [写入] 事件", socket);
            // 简单起见，这个 Poller 也处理写入事件
            socket.getPoller().register(socket, SelectionKey.OP_WRITE);
        } else { // Connection closed
            // 关闭连接
            connections.remove(socket);
        }
        return state;
    }

    /**
     * 通道超时或关闭时移除对应的 Processor，防止内存泄露
     * 
     * @param socket NioChannel
     */
    public void release(NioChannel socket) {
        Processor p = connections.remove(socket);
        if (p != null) {
            log.debug("释放通道 [{}] 关联的 Processor [{}]", socket, p);
        }
    }
    
    /** 抽象方法，创建一个具体的协议处理器 */
    public abstract  Processor createProcessor();
}

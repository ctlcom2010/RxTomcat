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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 接收 Socket 连接
 * 
 * @author tonwu.net
 */
public class Acceptor implements Runnable {
    final static Logger log = LoggerFactory.getLogger(Acceptor.class);
    
    /** 自定义通道注册事件 */
    public static final int OP_REGISTER = 0x100;

    private NioEndpoint endpoint;

    public Acceptor(NioEndpoint nioEndpoint) {
        endpoint = nioEndpoint;
    }

    @Override
    public void run() {
        while (endpoint.isRunning()) {
            try {
                // 申请一个连接名额
                endpoint.acquire();
                // 阻塞等待 socket 连接
                SocketChannel socket = endpoint.accept();
                try {
                    // 设置成非阻塞模式
                    socket.configureBlocking(false);
                    socket.socket().setTcpNoDelay(true);
                    socket.socket().setSoTimeout(endpoint.getSoTimeout());
                    // 封装成 NioChannel 对象
                    NioChannel channel = new NioChannel(socket);
                    // 将 NioChannel 对象插入 poller 的队列中，并指定关注的事件
                    endpoint.getPoller().register(channel, OP_REGISTER);
                    log.debug("接收通道 [{}] 连接", channel);
                } catch (Throwable t) {
                    log.error("", t);
                    try {
                        // 发生异常，释放一个连接名称，断开连接
                        // 这里的异常一般是由客户端连接后立刻又断开引起
                        endpoint.release();
                        socket.socket().close();
                        socket.close();
                    } catch (IOException ignore) {
                    }
                }
            } catch (SocketTimeoutException ste) {// ignore
                log.info(ste.getMessage());
            } catch (Throwable t) {
                log.error("Socket accept failed.", t);
            }
        }
    }
}

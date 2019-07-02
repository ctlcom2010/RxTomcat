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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.net.Handler.SocketState;

/**
 * 事件多路复用器，负责处理读写事件的通知，包括非阻塞和模拟阻塞，同时处理超时的通道
 * 
 * @author tonwu.net
 */
public class Poller implements Runnable {
    final static Logger log = LoggerFactory.getLogger(Poller.class);

    private NioEndpoint endpoint;

    /** 多路复用器 */
    private Selector selector;

    private volatile boolean close = false;

    /** 检查超时的最短时间间隔 */
    private long nextExpiration = 0;

    /**
     * 使用并发队列与 Acceptor 线程协作完成通道的注册，主要是因为 Acceptor 不能直 接注册到 Selector 上，可能会导致死锁
     */
    private ConcurrentLinkedQueue<NioChannel> events = new ConcurrentLinkedQueue<>();

    public Poller(NioEndpoint nioEndpoint) throws IOException {
        endpoint = nioEndpoint;
        selector = Selector.open();
    }

    @Override
    public void run() {
        int keyCount = 0; // 就绪key的个数
        while (true) {
            // 事件队列是否非空
            boolean hasEvents = false;
            try {
                if (!close) {
                    // 处理通道事件队列
                    hasEvents = events();
                    // 检查是否有通道发生读或写事件
                    keyCount = selector.select(5000);
                }

                if (close) { // 处理关闭
                    timeout(0, false);
                    selector.close();
                    break;
                }
            } catch (Throwable e) {
                log.error("", e);
                continue;
            }

            // 超时或者被 wakeup
            if (keyCount == 0)
                hasEvents = (hasEvents | events());

            Iterator<SelectionKey> iterator = keyCount > 0 ? selector.selectedKeys().iterator() : null;
            while (iterator != null && iterator.hasNext()) {
                final SelectionKey key = iterator.next();
                final NioChannel channel = (NioChannel) key.attachment();

                if (channel != null && (key.isReadable() || key.isWritable())) {
                    channel.access();
                    if (log.isDebugEnabled()) {
                        log.debug("通道 [{}] 发生 [可{}] I/O 事件，从其关注的事件中 [移除已就绪] 的事件", channel, key.isReadable() ? "读" : "写");
                    }
                    // 在当前 Poller 上移除已就绪的事件
                    int interestOps = key.interestOps() & (~key.readyOps());
                    key.interestOps(interestOps);
                    channel.interestOps(interestOps);

                    // 处理模拟阻塞
                    if (channel.getWriteLatch() != null) {
                        log.debug("模拟阻塞'写' - 通道 [{}] 已 [可写]", channel);
                        channel.getWriteLatch().countDown();
                        continue;
                    }
                    if (channel.getReadLatch() != null) {
                        log.debug("模拟阻塞'读' - 通道 [{}] 已 [可读]", channel);
                        channel.getReadLatch().countDown();
                        continue;
                    }
                    
                    if (log.isDebugEnabled()) {
                        log.debug("提交通道 [{}] 到线程池，处理发生的 [{}] 事件", channel, key.isReadable() ? "读取" : "写入");
                    }
                    
                    // 交给线程池
                    try {
                        endpoint.getExecutor().execute(new Runnable() {
                            @Override
                            public void run() {
                                SocketState state = endpoint.getHandler().handle(channel);
                                if (state == SocketState.CLOSED) {
                                    log.debug("关闭通道 [{}] 连接", channel);
                                    cancelledKey(key);
                                }
                            }
                        });
                    } catch (RejectedExecutionException t) {
                        log.error("提交失败，关闭通道", t);
                        // 提交失败，关闭通道
                        cancelledKey(key);
                    } catch (Throwable t) {
                        
                    }
                }
                iterator.remove();
            }
            // 处理超时
            timeout(keyCount, hasEvents);
        }
    }

    /**
     * 当满足以下条件时，才执行处理超时：<br>
     * - select() 调用超时（表示负载不大）<br>
     * - nextExpiration 时间已过<br>
     * - server socket 正在关闭
     * 
     * @param keyCount
     *            大于0，有 I/O 事件发生，否则没有
     * @param hasEvents
     *            true events队列有事件处理；false 事件队列为空
     */
    private void timeout(int keyCount, boolean hasEvents) {
        long now = System.currentTimeMillis();
        if (nextExpiration > 0 && (keyCount > 0 || hasEvents) && (now < nextExpiration) && !close) {
            // 如果处理了事件，或者小于最小间隔就不检查超时，只在空闲的时候检查
            return;
        }
        for (SelectionKey key : selector.keys()) {
            try {
                NioChannel channel = (NioChannel) key.attachment();
                if (channel == null) { // 没有附加对象，关闭连接
                    cancelledKey(key);
                } else if ((channel.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ
                        || (channel.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
                    // 仅仅检测当前关注读或写的通道
                    long delta = now - channel.getLastAccess();
                    boolean isTimedOut = delta > endpoint.getSoTimeout();
                    if (isTimedOut) {
                        log.debug("通道 [{}] 读或写超时", channel);
                        // 超时关闭连接
                        key.interestOps(0);
                        channel.interestOps(0);// 移除所有关注的事件，避免重复调用？
                        cancelledKey(key);
                    }
                }
            } catch (CancelledKeyException ckx) {
                log.debug("", ckx);
                cancelledKey(key);
            }
        }
        // 设置下一次检查的时间点
        nextExpiration = System.currentTimeMillis() + 1000;
    }

    /** 关闭通道 */
    public void cancelledKey(SelectionKey key) {
        NioChannel socket = (NioChannel) key.attach(null);
        if (socket != null) {
            // 释放连接可能占用的 Processor
            endpoint.getHandler().release(socket);
            // 取消 key
            if (key.isValid())
                key.cancel();
            log.debug("关闭通道 [{}] 连接", socket);
            // 关闭连接
            try {
                socket.ioChannel().close();
            } catch (IOException e) {
                log.debug("Channel close failed", e);
            }
            // 释放一个连接名额
            endpoint.release();
        }
    }

    /**
     * 停止 poller 线程
     */
    protected void destroy() {
        close = true;
        // 唤醒 Selector
        selector.wakeup();
    }

    /**
     * 将通道插入协作队列中
     * 
     * @param channel
     *            待插入通道
     * @param interestOps
     *            通道关注的事件
     */
    public void register(NioChannel channel, int interestOps) {
        // 关联 poller
        channel.setPoller(this);
        // 关联关注的事件
        channel.interestOps(interestOps);
        // 入队列
        events.offer(channel);
        // 唤醒 Selector 进行处理
        selector.wakeup();
    }

    /**
     * 遍历事件队列中并处理通道的注册或声明。队列中的元素要么是新连接的通道，要么 是旧的通道要在此 Poller 上重新声明关注读或写事件
     * 
     * @return true 事件队列非空，否则 false
     */
    public boolean events() {
        boolean hasEvent = false;
        NioChannel channel = null;

        while ((channel = events.poll()) != null) {
            hasEvent = true;
            SocketChannel sc = channel.ioChannel();
            int eventOps = channel.interestOps();
            if (eventOps == Acceptor.OP_REGISTER) {
                try {
                    log.debug("注册新的通道 [{}]，并声明关注 [读取] 事件", channel);
                    // 注册通道
                    sc.register(selector, SelectionKey.OP_READ, channel);
                } catch (Exception e) {
                    log.error("新通道 [" +  channel + "] 注册失败", e);
                }
            } else if (eventOps == SelectionKey.OP_READ || eventOps == SelectionKey.OP_WRITE) {
                // 重新在此 Poller 上声明关注读或写事件
                SelectionKey key = sc.keyFor(channel.getPoller().getSelector());
                try {
                    if (key != null) {
                        // 将重新关注的事件合并到现有的事件集合中
                        int ops = key.interestOps() | channel.interestOps();
                        key.interestOps(ops);
                        channel.interestOps(ops);
                    } else { } // The key was cancelled
                } catch (CancelledKeyException ckx) {
                    cancelledKey(key);
                }
            }
        }
        return hasEvent;
    }

    public Selector getSelector() {
        return selector;
    }

    public NioEndpoint getEndpoint() {
        return endpoint;
    }
}

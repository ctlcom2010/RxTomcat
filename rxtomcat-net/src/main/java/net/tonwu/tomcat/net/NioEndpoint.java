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
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 负责整个服务的启动、停止和初始化
 * 
 * @author tonwu.net
 */
public class NioEndpoint {
    final static Logger log = LoggerFactory.getLogger(NioEndpoint.class);
    
    private volatile boolean running = false;

    private ServerSocketChannel serverSock;
    private int port = 10393;
    private int soTimeout = 60000; // 60s
    
    /** 已完成 3 次握手，还没有被应用层接收的连接队列大小 */
    private int acceptCount = 100;// backlog
    
    /* 线程池 */
    private ExecutorService executor;
    private int maxThreads = 5;

    /* 控制总连接数的信号量 */
    private Semaphore connectionLimit;
    private int maxConnections = 2;

    private Poller poller;
    private Acceptor acceptor;
    
    private Handler handler;
    
    public void init() throws IOException {
        serverSock = ServerSocketChannel.open();
        serverSock.socket().bind(new InetSocketAddress(port), acceptCount);
        serverSock.configureBlocking(true);
        serverSock.socket().setSoTimeout(soTimeout);

        connectionLimit = new Semaphore(maxConnections);
    }

    public void start() throws IOException {
        running = true;
        // 初始化线程池
        executor = Executors.newFixedThreadPool(maxThreads, new ThreadFactory() {
            final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread td = new Thread(r, "pool-thread-" + threadNumber.getAndIncrement());
                return td;
            }
        });

        // 初始化并启动 Poller 和 Acceptor 线程
        poller = new Poller(this);
        Thread pollerThread = new Thread(poller, "poller");
        pollerThread.start();

        acceptor = new Acceptor(this);
        Thread acceptorThread = new Thread(acceptor, "acceptor");
        acceptorThread.start();
        
        log.info("Listening port [{}]", port);
    }

    public void stop() {
        running = false;
        poller.destroy();
        executor.shutdownNow();
    }

    /** 申请一个连接名额 */
    public void acquire() throws InterruptedException {
        if (maxConnections == -1)
            return;
        connectionLimit.acquire();
    }

    /** 释放一个连接名额 */
    public void release() {
        if (maxConnections == -1)
            return;
        connectionLimit.release();
    }

    public SocketChannel accept() throws Exception {
        return serverSock.accept();
    }

    public boolean isRunning() {
        return running;
    }

    public Poller getPoller() {
        return poller;
    }

    public ServerSocketChannel getServerSock() {
        return serverSock;
    }

    public ExecutorService getExecutor() {
        return executor;
    }
    public void setHandler(Handler handler) {
        this.handler = handler;
    }
    public Handler getHandler() {
        return handler;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public void setPort(int port) {
        this.port = port;
    }
}

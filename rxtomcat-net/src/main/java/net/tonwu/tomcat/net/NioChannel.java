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

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 对 SocketChannel 的封装，主要包含两个 ByteBuffer 用于读和写；
 * 使用两个 CountDownLatch 实现模拟阻塞，并且提供阻塞读和写的方法
 * 
 * @author tonwu.net
 */
public class NioChannel {
    final static Logger log = LoggerFactory.getLogger(NioChannel.class);
    
    private SocketChannel socket;
    private ByteBuffer readbuff;
    private ByteBuffer writebuff;

    private Poller poller;
    private int interestOps = 0;

    private long timeout = 100000;
    private long lastAccess = -1;

    private CountDownLatch writeLatch;
    private CountDownLatch readLatch;

    public NioChannel(SocketChannel socket) {
        this.socket = socket;
        readbuff = ByteBuffer.allocate(2 * 8192);
        writebuff = ByteBuffer.allocate(2 * 8192);
        lastAccess = System.currentTimeMillis();
    }

    /**
     * 从底层通道读取数据
     * 
     * @param dst 目标缓冲区，进入此方法前需要切换成写入模式
     * @param block true 模拟阻塞读取，false 非阻塞读取
     * @return 读取的字节数
     * @throws IOException
     */
    public int read(ByteBuffer dst, boolean block) throws IOException {
        int n = 0;
        if (block) {// 模拟阻塞
            log.debug("模拟阻塞读取 - 从通道 [{}] 读取", this);
            boolean timedout = false; // 是否超时
            int keyCount = 1; // 假设通道有数据可读
            long startTime = System.currentTimeMillis();
            while (!timedout) {
                if (keyCount > 0) {
                    n = socket.read(dst);
                    if (n != 0) {
                        log.debug("  阻塞读取 [{}B] 字节", n);
                        break; // -1 或者已经读到了数据
                    }
                }
                // 等待读锁
                readLatch = new CountDownLatch(1);
                // 注册 Pooler 读事件，这里是重新声明关注读
                poller.register(this, SelectionKey.OP_READ);
                try {
                    log.debug("  阻塞等待通道 [{}] 发生 [可读] 事件", this);
                    // 阻塞等待可写
                    readLatch.await(timeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignore) {
                }

                if (readLatch.getCount() == 0) {
                    keyCount = 1;
                    readLatch = null;
                } else {// Interrupted 无数据可读，检查超时
                    keyCount = 0;
                    timedout = (System.currentTimeMillis() - startTime) >= timeout;
                }
            }
            if (timedout) {// 超时异常
                throw new SocketTimeoutException();
            }
        } else {
            n = socket.read(readbuff);
            log.debug("从通道 [{}] 非阻塞读取 [{}B] 字节", this, n);
        }
        return n;
    }
    /**
     * 阻塞把响应体数据发送到客户端，重置缓冲区，以供写入
     * 
     * @throws IOException
     */
    public void flush() throws IOException {
        writebuff.flip();
        if (writebuff.remaining() > 0) {
            log.debug("模拟阻塞写入 - 将响应体 [{}B] 数据写入通道 [{}]", writebuff.remaining(), this);
        }
        while (writebuff.hasRemaining()) { // TODO 超时处理
            int n = socket.write(writebuff);
            if (n == -1) throw new EOFException();
            if (n > 0) { // write success
                log.debug("  阻塞写入 [{}B] 字节", n);
                continue;
            }
            
          writeLatch = new CountDownLatch(1);
            // 注册 Pooler 写事件
            poller.register(this, SelectionKey.OP_WRITE);
            try {
                log.debug("  阻塞等待通道 [{}] 发生 [可写] 事件", this);
                // 阻塞等待可写
                writeLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            writeLatch = null;
        }
        writebuff.clear();
    }
    
    public CountDownLatch getWriteLatch() {
        return writeLatch;
    }

    public CountDownLatch getReadLatch() {
        return readLatch;
    }

    public ByteBuffer getReadBuffer() {
        return readbuff;
    }

    public ByteBuffer getWriteBufffer() {
        return writebuff;
    }

    public int interestOps() {
        return interestOps;
    }

    public int interestOps(int ops) {
        this.interestOps = ops;
        return ops;
    }

    public void access() {
        lastAccess = System.currentTimeMillis();
    }

    public long getLastAccess() {
        return lastAccess;
    }

    public Poller getPoller() {
        return poller;
    }

    public void setPoller(Poller poller) {
        this.poller = poller;
    }

    public SocketChannel ioChannel() {
        return socket;
    }

    public int read(ByteBuffer dst) throws IOException {
        return socket.read(dst);
    }
    public int write(ByteBuffer src) throws IOException {
        return socket.write(src);
    }

    @Override
    public String toString() {
        String retv = null;
        try {
            retv = socket.getRemoteAddress().toString();
        } catch (IOException e) {
            retv = super.toString();
        }
        return retv;
    }
}

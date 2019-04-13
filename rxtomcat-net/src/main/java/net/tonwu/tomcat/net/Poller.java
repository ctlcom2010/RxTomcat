package net.tonwu.tomcat.net;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.tonwu.tomcat.util.log.Log;
import net.tonwu.tomcat.util.log.LoggerFactory;

public class Poller implements Runnable {
    final static Log log = LoggerFactory.getLogger(Poller.class);

    private NioEndpoint endpoint;

    private Selector selector;
    
    private volatile boolean close = false;
    
    /** 检查超时的最短时间间隔 */
    private long nextExpiration = 0;
    
    private int keyCount = 0;
    
    private ConcurrentLinkedQueue<NioChannel> events = new ConcurrentLinkedQueue<>();

    public Poller(NioEndpoint nioEndpoint) throws IOException {
        endpoint = nioEndpoint;
        selector = Selector.open();
    }

    @Override
    public void run() {
        while (true) {
            boolean hasEvents = false;
            try {
                if (!close) {
                    hasEvents = events();
                    keyCount = selector.select(5000);
                }
                
                if (close) {
                    timeout(0, false);
                    selector.close();
                    break;
                }
            } catch (Throwable e) {
                e.printStackTrace();
                continue;
            }

            // 超时或者被 wakeup
            if ( keyCount == 0 ) hasEvents = (hasEvents | events());

            Iterator<SelectionKey> iterator = keyCount > 0 ? selector.selectedKeys().iterator() : null;
            while (iterator != null && iterator.hasNext()) {
                SelectionKey key = iterator.next();
                NioChannel channel = (NioChannel) key.attachment();

                if (channel != null && (key.isReadable() || key.isWritable())) {
                    channel.access();
                    // 在当前 Poller 上移除已就绪的事件
                    int interestOps = key.interestOps() & (~key.readyOps());
                    key.interestOps(interestOps);
                    channel.interestOps(interestOps);
                    // 交给线程池
                    try {
                        SocketProcessor sp = new SocketProcessor(channel);
                        endpoint.getExecutor().execute(sp);
                    } catch (Throwable t) {
                        // 提交失败，关闭通道
                        cancelledKey(key);
                    }
                }
                iterator.remove();
            }
            timeout(keyCount, hasEvents);
        }
    }
    /**
     * 当满足以下条件时，才执行处理超时：<br>
     * - select() 调用超时（表示负载不大）<br> 
     * - nextExpiration 时间已过<br>
     * - server socket 正在关闭
     * 
     * @param keyCount 大于0，有 I/O 事件发生，否则没有
     * @param hasEvents true events队列有事件处理；false 事件队列为空
     */
    private void timeout(int keyCount, boolean hasEvents) {
        long now = System.currentTimeMillis();
        if (nextExpiration > 0 && (keyCount > 0 || hasEvents) && (now < nextExpiration) && !close) {
            return;
        }
        for (SelectionKey key : selector.keys()) {
            try {
                NioChannel channel = (NioChannel) key.attachment();
                if (channel == null) {
                    cancelledKey(key);
                } else if ((channel.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ
                        || (channel.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
                    // 仅仅检测那些对读或写的通道
                    long delta = now - channel.getLastAccess();
                    boolean isTimedOut = delta > endpoint.getSoTimeout();
                    if (isTimedOut) {
                        try {
                            log.debug("Timeout: ", channel.ioChannel().getRemoteAddress());
                        } catch (IOException e) {
                        }
                        key.interestOps(0);
                        channel.interestOps(0);
                        cancelledKey(key);
                    }
                }
            } catch (CancelledKeyException ckx) {
                ckx.printStackTrace();
                cancelledKey(key);
            }
        }
        // 设置下一次检查的时间点
        nextExpiration = System.currentTimeMillis() + 1000;
    }

    public void cancelledKey(SelectionKey key) {
        try {
            NioChannel socket = (NioChannel) key.attach(null);
            if (socket != null) {
                // 释放连接可能占用的 Processor
                endpoint.getHandler().release(socket);
            }
            if (key.isValid()) key.cancel();
            
            socket.ioChannel().close();
            
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    
    protected void destroy() {
        close = true;
        selector.wakeup();
    }
    
    public void register(NioChannel channel, int interestOps) {
        channel.setPoller(this);
        channel.interestOps(interestOps);
        events.offer(channel);
        selector.wakeup();
    }

    public boolean events() {
        boolean hasEvent = false;
        NioChannel channel = null;

        while ((channel = events.poll()) != null) {
            hasEvent = true;
            SocketChannel sc = channel.ioChannel();
            try {
                int eventOps = channel.interestOps();
                if (eventOps == Acceptor.OP_REGISTER) {
                    // 注册通道
                    sc.register(selector, SelectionKey.OP_READ, channel);
                } else if (eventOps == SelectionKey.OP_READ || eventOps == SelectionKey.OP_WRITE) {
                    // 重新在此 Poller 上声明关注读或写事件
                    SelectionKey key = sc.keyFor(channel.getPoller().getSelector());
                    if (key != null) {
                        int ops = key.interestOps() | channel.interestOps();
                        key.interestOps(ops);
                        channel.interestOps(ops);
                    } else {}// The key was cancelled
                }
            } catch (ClosedChannelException e) {
                e.printStackTrace();
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

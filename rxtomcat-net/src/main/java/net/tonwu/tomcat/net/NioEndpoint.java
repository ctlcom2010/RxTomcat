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

import net.tonwu.tomcat.util.log.Log;
import net.tonwu.tomcat.util.log.LoggerFactory;

public class NioEndpoint {
    final static Log log = LoggerFactory.getLogger(NioEndpoint.class);

    private volatile boolean running = false;

    private ServerSocketChannel serverSock;
    private int port = 10393;
    private int soTimeout = 150000;

    private ExecutorService executor;
    private int maxThreads = 5;
    private boolean daemon = false;

    private Semaphore connectionLimit;
    private int maxConnections = 2;
    
    private Poller poller;
    private Acceptor acceptor;
    private Handler handler;
    
    public void init() throws IOException {
        serverSock = ServerSocketChannel.open();
        serverSock.socket().bind(new InetSocketAddress(port));
        serverSock.configureBlocking(true);
        serverSock.socket().setSoTimeout(soTimeout);
        
        connectionLimit = new Semaphore(maxConnections);
    }
    
    public void setProcessorClassName(String processorClassName) {
        if (handler == null) {
            handler = new Handler();
        }
        handler.setProcessor(processorClassName);
    }

    public void start() throws IOException {
        running = true;

        executor = Executors.newFixedThreadPool(maxThreads, new ThreadFactory() {
            final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread td = new Thread(r, "pool-thread-" + threadNumber.getAndIncrement());
                td.setDaemon(daemon);
                return td;
            }
        });

        poller = new Poller(this);
        Thread pollerThread = new Thread(poller, "poller");
        pollerThread.setDaemon(daemon);
        pollerThread.start();

        acceptor = new Acceptor(this);
        Thread acceptorThread = new Thread(acceptor, "acceptor");
        acceptorThread.setDaemon(daemon);
        acceptorThread.start();
        log.info("NioEndpoint Started, Port: {}", port);
    }

    public void releaseConnectionLatch() {
        connectionLimit.release(maxConnections);
    }
    public void countUpOrAwaitConnection() throws InterruptedException {
        if (maxConnections==-1) return;
        connectionLimit.acquire();
    }
    public void countDownConnection() {
        if (maxConnections==-1) return;
        connectionLimit.release();
    }
    
    public void stop() {
        running = false;
        poller.destroy();
        executor.shutdownNow();
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
    
    public Handler getHandler() {
        return handler;
    }
    
    public int getSoTimeout() {
        return soTimeout;
    }

}

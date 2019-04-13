package net.tonwu.tomcat.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;

public class NioChannel {

    private SocketChannel socket;
    private ByteBuffer readbuf;
    private ByteBuffer writebuf;

    private Poller poller;
    private int interestOps = 0;

    private long lastAccess = -1;

    @SuppressWarnings("unused")
    private CountDownLatch readLatch;
    @SuppressWarnings("unused")
    private CountDownLatch writeLatch;

    public NioChannel(SocketChannel socket) {
        this.socket = socket;
        readbuf = ByteBuffer.allocate(8192);
        writebuf = ByteBuffer.allocate(8192);
        lastAccess = System.currentTimeMillis();
    }

    public ByteBuffer readBuf() {
        return readbuf;
    }

    public ByteBuffer writeBuf() {
        return writebuf;
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
}

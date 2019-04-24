package net.tonwu.tomcat.http;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;

import net.tonwu.tomcat.http.ActionHook.ActionCode;
import net.tonwu.tomcat.net.NioChannel;

public class OutputBuffer {
    public static final byte[] HTTP_1_1 = "HTTP/1.1 ".getBytes();
    public static final byte[] CRLF_BYTES = "\r\n".getBytes();
    
    private ByteBuffer headerBuffer;
    private BodyFilter bodyFilter;
    
    private NioChannel socket;
    private RawResponse resp;
    
    public OutputBuffer() {
    }

    public void writeStatus() {
        headerBuffer.put(Constants.HTTP_1_1);
        int status = resp.getStatus();
        headerBuffer.put(String.valueOf(status).getBytes());
        headerBuffer.put(Constants.msg(status).getBytes());
        headerBuffer.put(Constants.CRLF);
    }
    public void writeHeaders() {
        for (Entry<String, String> header : resp.headers().entrySet()) {
            String value = header.getValue();
            headerBuffer.put(header.getKey().getBytes());
            headerBuffer.put(Constants.COLON);
            headerBuffer.put(Constants.SP);
            headerBuffer.put(value.getBytes());
            headerBuffer.put(Constants.CRLF);
        }
        headerBuffer.put(Constants.CRLF);
    }
    
    public void commit() throws IOException {
        resp.setCommited(true);
        headerBuffer.flip();
        write(headerBuffer);
    }
    
    public void setSocket(NioChannel socket) {
        this.socket = socket;
        headerBuffer = ByteBuffer.allocate(8192);
        headerBuffer.clear();
    }

    public void setBodyFilter(BodyFilter body) {
        this.bodyFilter = body;
        body.setResponse(resp);
        body.setBuffer(this);
    }

    public void setRawResponse(RawResponse response) {
        resp = response;
    }

    public void doWrite(byte[] bytes) throws IOException {
        if (!resp.isCommited()) {
            resp.action(ActionCode.COMMIT, null);
        }
        ByteBuffer src = ByteBuffer.wrap(bytes);
        if (bodyFilter != null) {
        	bodyFilter.doWrite(src);
        } else {
        	write(src);
        }
    }
    public void end() throws IOException {
    	if (bodyFilter != null) {
    		bodyFilter.end();
    	}
    	flush();
    }
    
    public void write(byte[] src) throws IOException {
    	write(ByteBuffer.wrap(src));
    }
    public void write(ByteBuffer src) throws IOException {
    	write(src, false);
    }
    // 写入通道待发送缓冲区
    public void write(ByteBuffer src, boolean flip) throws IOException {
    	if (flip) src.flip();
    	ByteBuffer dst = socket.writeBuf();
    	while (src.hasRemaining()) {
    		if (dst.position() == dst.capacity()
    				|| dst.remaining() == 0) {
    			flush();
    		}
    		dst.put(src);
    	}
    	src.clear();
    }
    
    // 阻塞写入通道，这里和非阻塞用的是同一个 Poller
    public void flush() throws IOException {
    	ByteBuffer buf = socket.writeBuf();
    	buf.flip();
    	while (buf.hasRemaining()) { // TODO 超时处理
    		int n = socket.write(buf);
    		if (n == -1) throw new EOFException();
    		if (n > 0) { // write success
    			continue;
    		}
    		
    		socket.writeLatch = new CountDownLatch(1);
    		// 注册 Pooler 写事件
    		socket.getPoller().register(socket, SelectionKey.OP_WRITE);
    		try {
    			// 阻塞等待可写
				socket.writeLatch.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    		socket.writeLatch = null;
    	}
    	buf.clear();
    }
}

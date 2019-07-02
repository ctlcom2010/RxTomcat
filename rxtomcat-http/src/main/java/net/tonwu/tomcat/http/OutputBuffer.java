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
package net.tonwu.tomcat.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.http.ActionHook.ActionCode;
import net.tonwu.tomcat.net.NioChannel;

/**
 * 编码响应
 * 
 * @author tonwu.net
 */
public class OutputBuffer implements Recyclable {
    final static Logger log = LoggerFactory.getLogger(OutputBuffer.class);
    
    public static final byte[] HTTP_1_1 = "HTTP/1.1 ".getBytes();
    public static final byte[] CRLF_BYTES = "\r\n".getBytes();
    
    private ByteBuffer byteBuffer;
    private BodyCodec codec;
    
    private NioChannel socket;
    private RawResponse resp;
    
    public OutputBuffer(RawResponse resp) {
        this.resp = resp;
    }
    public void setSocket(NioChannel socket) {
        this.socket = socket;
        byteBuffer = socket.getWriteBufffer();
        byteBuffer.clear();
    }
    /**
     * 将响应头写入到缓冲区
     * 
     * @throws IOException
     */
    public void commit() throws IOException {
        resp.setCommitted(true);
        int pos = byteBuffer.position();
        
        // 1. 将状态行写入缓冲区
        byteBuffer.put(HttpToken.HTTP_1_1);
        int status = resp.getStatus();
        byteBuffer.put(String.valueOf(status).getBytes());
        
        byte[] msg = null;
        if (resp.getMessage() != null) {
            msg = resp.getMessage().getBytes(resp.getCharacterEncoding());
        } else {
            msg = HttpToken.msg(status).getBytes();
        }
        
        byteBuffer.put(msg);
        byteBuffer.put(HttpToken.CRLF);
        
        // 2. 将响应头域写入缓冲区
        for (Entry<String, String> header : resp.headers().entrySet()) {
            byte[] name = header.getKey().getBytes();
            if ("Set-Cookie".equalsIgnoreCase(header.getKey())) {
                for (String cookie : header.getValue().split(";")) {
                    writeHeader(name, cookie.getBytes());
                }
            } else {
                writeHeader(name, header.getValue().getBytes());
            }
        }
        byteBuffer.put(HttpToken.CRLF);
        
        log.debug("将响应头部 [{}B] 数据写入提交到底层缓冲区", (byteBuffer.position() - pos + 1));
    }
    
    private void writeHeader(byte[] name, byte[] value) {
        byteBuffer.put(name);
        byteBuffer.put(HttpToken.COLON);
        byteBuffer.put(HttpToken.SP);
        byteBuffer.put(value);
        byteBuffer.put(HttpToken.CRLF);
    }
    
    /**
     * 写入响应体数据前，响应头确认已写入缓冲区
     * 
     * @param src 待写入数据
     * @throws IOException
     */
    public void writeBody(ByteBuffer src) throws IOException {
        if (!resp.isCommitted()) {
            resp.action(ActionCode.COMMIT, null);
        }
        
        if (src.remaining() > 0) {
            log.debug("写入响应体数据 [{}B]", src.remaining());
            codec.doWrite(this, src);
        }
    }
    
    public void end() throws IOException {
        if (!resp.isCommitted()) {
            resp.action(ActionCode.COMMIT, null);
        }
        
        if (codec != null) {
            codec.endWrite(this);
        }
        flush();
    }
    
    public void write(byte[] b) throws IOException {
    	write(ByteBuffer.wrap(b));
    }
    public void write(ByteBuffer b) throws IOException {
    	write(b, false);
    }
    // 写入通道待发送缓冲区
    public void write(ByteBuffer src, boolean flip) throws IOException {
    	if (flip) src.flip();
    	while (src.hasRemaining()) {
    	    // 无空间可供写入
    		if (byteBuffer.remaining() == 0) {
    		    socket.flush(); // 把数据发送到客户端
    		}
    		byteBuffer.put(src);
    	}
    	src.clear();
    	// 以防超时
    	socket.access();
    }
    public void flush() throws IOException {
        socket.flush();
    }
    public void setBodyCodec(BodyCodec body) {
        this.codec = body;
    }

    public void setRawResponse(RawResponse response) {
        resp = response;
    }
    @Override
    public void recycle() {
        resp.recycle();
        
        byteBuffer.clear();
        codec = null;
    }
}

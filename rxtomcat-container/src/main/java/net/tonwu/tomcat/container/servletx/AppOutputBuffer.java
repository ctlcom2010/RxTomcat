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
package net.tonwu.tomcat.container.servletx;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import javax.servlet.ServletOutputStream;

import net.tonwu.tomcat.http.ActionHook.ActionCode;
import net.tonwu.tomcat.http.RawResponse;
/**
 * 实现 ServletOutputStream 往底层写入响应体数据
 * <p>
 * 这个原生的 ByteBuffer 确实不好用啊，Tomcat 封装的方法还可以
 * 
 * @author tonwu.net
 */
public class AppOutputBuffer extends ServletOutputStream {
    private RawResponse rawResp;
    
    private String encoding;
    
    private ByteBuffer bodyBytes;
    private CharBuffer bodyChars;
    
    /** 不响应数据，只发送响应头，用于 redirect */
    private boolean suspended = false;
    private boolean isNew = true;
    
    public AppOutputBuffer(RawResponse rawResp) {
        this.rawResp = rawResp;
        
        bodyBytes = ByteBuffer.allocate(8196);
        clear(bodyBytes);
        bodyChars = CharBuffer.allocate(8196);
        clear(bodyChars);
    }

    @Override
    public void write(int b) throws IOException {
        writeByte((byte) b);
    }
    
    public void writeByte(byte c) throws IOException {
        if (isFull(bodyBytes)) {
            flushByteBuffer();
        }
        transfer(c, bodyBytes);
        isNew = false;
    }
    public void writeChar(char c) throws IOException {
        if (isFull(bodyChars)) {
            flushCharBuffer();
        }
        transfer(c, bodyChars);
        isNew = false;
    }
    
    public void write(char[] cbuf, int off, int len) throws IOException {
        if (len <= bodyChars.capacity() - bodyChars.limit()) { // remainning
            transfer(cbuf, off, len, bodyChars);
            return;
        }
        
        // 发送已有数据
        flushCharBuffer();
        
        // 发送剩余的字符序列
        realWriteChars(CharBuffer.wrap(cbuf, off, len));
        isNew = false;
    }
    
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (bodyBytes.remaining() == 0) { // 首次写入时等于 0
            appendByteArray(b, off, len);
        } else {
            int n = transfer(b, off, len, bodyBytes);
            len = len - n;
            off = off + n;
            
            flushByteBuffer();
            if (isFull(bodyBytes)) {
                flushByteBuffer();
                appendByteArray(b, off, len);
            }
        }
        isNew = false;
    }
    
    private void appendByteArray(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return;
        }

        // 发送剩余数据
        int limit = bodyBytes.capacity();
        while (len >= limit) {
            rawResp.doWrite(ByteBuffer.wrap(b, off, len));
            len = len - limit;
            off = off + limit;
        }
        // 还有剩余数据，写入 bodyBytes 中
        if (len > 0) {
            transfer(b, off, len, bodyBytes);
        }
    }
    
    /**
     * 将 CharBuffer 转为 ByteBuffer 写入数据
     * 
     * @param from 待转的 CharBuffer
     * @throws IOException
     */
    private void realWriteChars(CharBuffer from) throws IOException {
        if (encoding == null) {
            encoding = rawResp.getCharacterEncoding();
        }
        
        if (from.hasRemaining()) {
            ByteBuffer bb = Charset.forName(encoding).encode(from);
            if (bb.remaining() <= bodyBytes.remaining()) {
                transfer(bb, bodyBytes);
            } else {
                flushByteBuffer();
                rawResp.doWrite(bb.slice());
            }
        }
    }
    
    @Override
    public void flush() throws IOException {
        if (suspended) return;
        
        if (bodyChars.remaining() > 0) {
            flushCharBuffer();
        }
        if (bodyBytes.remaining() > 0) {
            flushByteBuffer();
        }
        
        rawResp.action(ActionCode.FLUSH , null);
    }

    @Override
    public void close() throws IOException {
        if (suspended) return;
        
        if (bodyChars.remaining() > 0) {
            flushCharBuffer();
        }
        
        if (!rawResp.isCommitted()) {
            rawResp.setContentLength(bodyBytes.remaining());
        }
        flush();
        rawResp.action(ActionCode.CLOSE, null);
    }
    
    public void recycle() {
        clear(bodyBytes);
        clear(bodyChars);
        suspended = false;
        isNew = true;
    }
    
    public boolean isNew() {
        return isNew;
    }
    
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    // Tomcat 为了方便使用 ByteBuffer 定义的方法
    private void flushByteBuffer() throws IOException {
        if (bodyBytes.remaining() > 0) {
            rawResp.doWrite(bodyBytes.slice());
            clear(bodyBytes); // 切成可写模式
        }
    }

    private void flushCharBuffer() throws IOException {
        if (bodyChars.remaining() > 0) {
            realWriteChars(bodyChars.slice());
            clear(bodyChars); // 切成可写模式
        }
    }
    
    private void clear(Buffer buffer) {
        buffer.rewind().limit(0);
    }

    private boolean isFull(Buffer buffer) {
        return buffer.limit() == buffer.capacity();
    }

    private void toReadMode(Buffer buffer) {
        buffer.limit(buffer.position())
              .reset();
    }

    private void toWriteMode(Buffer buffer) {
        buffer.mark()
              .position(buffer.limit())
              .limit(buffer.capacity());
    }
    
    private int transfer(byte[] buf, int off, int len, ByteBuffer to) {
        toWriteMode(to);
        int min = Math.min(len, to.remaining());
        if (min > 0) {
            to.put(buf, off, min);
        }
        toReadMode(to);
        return min; // 返回写入数据长度
    }
    
    private int transfer(char[] buf, int off, int len, CharBuffer to) {
        toWriteMode(to);
        int min = Math.min(len, to.remaining());
        if (min > 0) {
            to.put(buf, off, min);
        }
        toReadMode(to);
        return min;// 返回写入数据长度
    }
    private void transfer(byte b, ByteBuffer to) {
        toWriteMode(to);
        to.put(b);
        toReadMode(to);
    }
    private void transfer(char b, CharBuffer to) {
        toWriteMode(to);
        to.put(b);
        toReadMode(to);
    }
    private void transfer(ByteBuffer from, ByteBuffer to) {
        toWriteMode(to);
        int min = Math.min(from.remaining(), to.remaining());
        if (min > 0) {
            int fromLimit = from.limit();
            from.limit(from.position() + min);
            to.put(from);
            from.limit(fromLimit);
        }
        toReadMode(to);
    }
}

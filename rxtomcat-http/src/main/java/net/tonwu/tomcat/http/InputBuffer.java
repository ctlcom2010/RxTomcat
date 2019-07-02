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

import static net.tonwu.tomcat.http.HttpToken.COLON;
import static net.tonwu.tomcat.http.HttpToken.CR;
import static net.tonwu.tomcat.http.HttpToken.LF;
import static net.tonwu.tomcat.http.HttpToken.QUESTION;
import static net.tonwu.tomcat.http.HttpToken.SP;

import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.net.NioChannel;

/**
 * 使用有限状态机解析 HTTP 协议请求行和请求体
 * 
 * @author tonwu.net
 */
public class InputBuffer implements Recyclable, BufferHolder {
    final Logger log = LoggerFactory.getLogger(InputBuffer.class);
    
    /** 当前解析状态 */
    private ParseStatus status = ParseStatus.METHOD;
    /**
     * 请求头解析状态
     * @author tonwu.net
     */
    public enum ParseStatus {
        METHOD, // 解析请求方法
        URI, // 解析请求 URI
        VERSION, // 解析协议版本
        QUERY, // 解析查询参数
        HEADER_NAME, // 解析头域名称
        HEADER_VALUE, // 解析头域值
        HEADER_END, // 解析一个头域完毕
        DONE // 解析完成
    }

    /** 正在解析请求头域 */
    private boolean parsingHeader = true;
    private int maxHeaderSize = 8192; // 请求头最大大小 k

    private BodyCodec codec;
    private RawRequest request;

    private NioChannel socket;
    
    /** byteBuffer 引用的是 NioChannel 内部的  readbuff */
    private ByteBuffer byteBuffer;
    
//  boolean swallowInput
    
    public InputBuffer(RawRequest request) {
        this.request = request;
    }

    public void setSocket(NioChannel socket) {
        this.socket = socket;
        byteBuffer = socket.getReadBuffer();
        byteBuffer.position(0).limit(0);
    }
    
    /**
     * 请求头信息在字节数组中结束的位置，即请求体数据开始的位置
     * Request Header End
     */
    private int rhend;
    
    private StringBuilder sb = new StringBuilder();
    private String takeString() {
        String retv = sb.toString();
        sb.setLength(0);
        return retv;
    }

    /**
     * 使用状态机的方法，遍历字节解析请求头（解析时没有进行严谨性校验）
     * 
     * @return true - 读取完成，false - 读取到部分请求头
     * @throws IOException
     */
    public boolean parseRequestLineAndHeaders() throws IOException {
        log.debug("解析请求行和请求 Headers");
        
        String headerName = null;
        do {
            // 缓冲区是否有数据可读
            if (byteBuffer.position() >= byteBuffer.limit()) {
                if (!fill(false)) {
                    return false; // 请求头不完整
                }
            }
            // 状态机解析请求头，这里直接存储字符串，Tomcat 是在使用时才转字符串
            byte chr = byteBuffer.get();
            switch (status) {
            case METHOD:
                if (chr == SP) {
                    request.setMethod(takeString());
                    sb.setLength(0);
                    status = ParseStatus.URI;
                } else {
                    sb.append((char) chr);
                }
                break;
            case URI:
                if (chr == SP || chr == QUESTION) {
                    request.setUri(takeString());
                    sb.setLength(0);
                    if (chr == QUESTION) {
                        status = ParseStatus.QUERY;
                        request.setQueryStartPos(byteBuffer.position());
                    } else {
                        status = ParseStatus.VERSION;
                    }
                } else {
                    sb.append((char) chr);
                }
                break;
            case QUERY: // 查询字符串特殊字符会被编码
                if (chr == SP) {
                    // 获取实际查询字符串的字节数据视图
                    int queryEndPos = byteBuffer.position();
                    ByteBuffer temp = byteBuffer.duplicate();
                    temp.position(request.getQueryStartPos()).limit(queryEndPos);
                    request.setQuery(temp.duplicate());
                    request.getQuery().mark();
                    
                    status = ParseStatus.VERSION;
                }
                break;
            case VERSION:
                if (chr == CR) {
                } else if (chr == LF) {
                    request.setProtocol(takeString());
                    status = ParseStatus.HEADER_NAME;
                } else {
                    sb.append((char) chr);
                }
                break;
            case HEADER_NAME:
                if (chr == COLON) {
                    headerName = takeString().toLowerCase();
                    status = ParseStatus.HEADER_VALUE;
                } else {
                    sb.append((char) chr);
                }
                break;
            case HEADER_VALUE:
                if (chr == CR) {
                } else if (chr == LF) {
                    // 这里有个空格没处理 ，trim 一下，也可以多加一个 HEADER_VALUE_START 状态来跳过空格
                    request.addHeader(headerName, takeString().trim().toLowerCase());
                    headerName = null;
                    status = ParseStatus.HEADER_END;
                } else {
                    sb.append((char) chr);
                }
                break;
            case HEADER_END:
                if (chr == CR) {
                } else if (chr == LF) {
                    // 请求头解析完毕
                    status = ParseStatus.DONE;
                    parsingHeader = false;
                    // 记录请求头数据在缓冲区结束的位置
                    rhend = byteBuffer.position();
                } else {
                    sb.append((char) chr);
                    status = ParseStatus.HEADER_NAME;
                }
                break;
            default:
                break;
            }
        } while (status != ParseStatus.DONE);
        
        log.debug("请求头部数据读取并解析完毕\r\n======Request======\r\n{}\r\n===================", request);
        
        return true;
    }
    
    /**
     * 从通道读取字节
     * 
     * @param block true 模拟阻塞读，false 非阻塞读
     * @return true - 有数据读取，false - 无数据可读
     * @throws IOException 通道已经关闭，继续读取时发生
     * @throws IllegalArgumentException 请求头太大，超过 8192KB
     * @throws EOFException 连接被关闭
     */
    public boolean fill(boolean block) throws IOException {
    	if (parsingHeader) {
    		// 如果正在解析请求头域数据
    		if (byteBuffer.limit() > maxHeaderSize) {
    			throw new IllegalArgumentException("请求头太大");
    		}
    	} else {
    		// 这里也把请求头部数据保留在读缓冲区中
    		// 所以重置 pos 和 limit 的位置，以重复利用请求头数据之后的空间来读取请求体
    		byteBuffer.limit(rhend).position(rhend);
    	}
    	// 记住当前 position 位置
    	byteBuffer.mark();
    	// 切换可写模式，并设置最大读取字节数
    	byteBuffer.limit(byteBuffer.capacity());
    	// 读取数据
    	int n = socket.read(byteBuffer, block);
    	// 切换成可读模式
    	byteBuffer.limit(byteBuffer.position()).reset();
    	if (n == -1) {
            throw new EOFException("EOF，通道被关闭");
        }
    	return n > 0;
    }
    
    /**
     * 模拟阻塞读取请求体数据
     * 
     * @param buffHolder 持有读取数据的字节视图，如果参数值为 null，表示不需要数据，单纯的读取
     * @return 因为是阻塞的，必定会返回一个大于 0 的数字，若返回 -1 连接肯定关闭了
     * @throws IOException
     */
    public int readBody(BufferHolder buffHolder) throws IOException {
        if (codec != null) {
            return codec.doRead(this, buffHolder);
        } else {
            return realReadBytes(buffHolder);
        }
    }
    /**
     * 从底层通道读取数据，并返回一个与结果对应视图 ByteBuffer
     * 
     * @param buffHolder 持有读取数据的字节视图，如果参数值为 null，表示不需要数据，单纯的读取
     * @return 返回实际读取的数据大小，-1 表示连接被关闭
     * @throws IOException
     */
    public int realReadBytes(BufferHolder buffHolder) throws IOException {
        if (byteBuffer.position() >= byteBuffer.limit()) {
            if(!fill(true)) {
                buffHolder.setByteBuffer(null);
                return -1;
            }
        }
        int length = byteBuffer.remaining();
        if (buffHolder != null) {
            // dst 与 byteBuffer 底层共用一个 byte[]
            buffHolder.setByteBuffer(byteBuffer.duplicate());
        }
        byteBuffer.position(byteBuffer.limit());
        return length;
    }
    
    private int maxPostSize = 1 * 1024 * 1024;
    
    /**
     * 存储 post 数据，最大 1M
     */
    private ByteBuffer body = ByteBuffer.allocate(maxPostSize); // 1MB
    private ByteBuffer bodyView = null; // 部分请求体数据
    
    /**
     * 解析 GET 和 POST 请求参数
     */
    public void readAndParseBody() {
        request.setParametersParsed(true);
        // 1. 解析查询参数 GET
        if (request.getQuery() != null) {
            request.getQuery().reset();
            byte[] queryBytes = new byte[request.getQuery().remaining()];
            request.getQuery().get(queryBytes);
            parseParameters(queryBytes);
        }
        
        // 2. 解析 post 请求参数并且是以键值对进行传输
        if (!"POST".contentEquals(request.getMethod()) || 
                !request.getContentType().contains("application/x-www-form-urlencoded")) {
            return;
        }
        
        // 3. 读取请求体数据
        body.clear();
        try {
            int len = request.getContentLength();
            if (len > 0) { // identity 传输编码
                if (len > maxPostSize) {
                    request.setParseParamFail(true);
                    return;
                }
                // 循环读取指定数量的字节
                int n = -1;
                while (len > 0 && ((n = readBody(this)) >= 0)) {
                    body.put(bodyView);
                    len -= n;
                }
            } else if ("chunked".equalsIgnoreCase(request.getHeader("transfer-encoding"))){
                // chunked 传输编码
                len = 0;
                int n = 0;
                while ((n = readBody(this)) >= 0) {
                    body.put(bodyView);
                    len += n;
                    if (len > maxPostSize) { // POST 数据太大了
                        request.setParseParamFail(true);
                        return;
                    }
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            request.setParseParamFail(true);
            return;
        }
        
        // 5. 解析参数
        body.flip();
        byte[] post = new byte[body.remaining()];
        body.get(post);
        parseParameters(post);
    }
    
    /**
     * 解析请求参数，查询参数，查询参数可以是这样 a=%E5%88%9B+a
     */
    private void parseParameters(byte[] body) {
        String params = new String(body, request.getEncoding());
        try {
            // 将形如 "%xy" 的编码，转为正确的字符串
            params = URLDecoder.decode(params, request.getEncoding().toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        
        // 参数格式为 "a=1&b=&c=2"
        String[] param = params.split("&");
        if (param != null && param.length > 0) {
            for (String ele : param) {
                if (ele.startsWith("=")) {
                    continue; // no name
                }
                try {
                    String[] nv = ele.trim().split("=");
                    String name = nv[0];
                    String value = nv.length > 1 ? nv[1] : "";
                    request.getParameters().put(name, value);
                } catch (Exception ignore) {
                }
            }
        }
    }

    public void setBodyCodec(BodyCodec body) {
        this.codec = body;
    }
    public void setRequest(RawRequest req) {
        request = req;
    }

    public void end() throws IOException {
        if (codec != null) {
            codec.endRead(this);
        }
    }
    
    @Override
    public void recycle() {
        request.recycle();
        
        status = ParseStatus.METHOD;
        parsingHeader = true;
        byteBuffer.clear();
        body.clear();
        codec = null;
        rhend = 0;
    }

    // BufferHolder Method
    @Override
    public void setByteBuffer(ByteBuffer buffer) {
        bodyView = buffer;
    }
    @Override
    public ByteBuffer getByteBuffer() {
        return bodyView;
    }
}

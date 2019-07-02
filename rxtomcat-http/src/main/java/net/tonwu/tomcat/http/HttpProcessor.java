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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.http.codecs.ChunkedCodec;
import net.tonwu.tomcat.http.codecs.IdentityCodec;
import net.tonwu.tomcat.net.Handler.SocketState;
import net.tonwu.tomcat.net.NioChannel;
import net.tonwu.tomcat.net.Processor;
/**
 * HTTP 协议处理器，与底层通道通信
 * 
 * @author tonwu.net
 */
public class HttpProcessor implements Processor, ActionHook {
    final static Logger log = LoggerFactory.getLogger(HttpProcessor.class);
    
    private InputBuffer inBuffer;
    private OutputBuffer outBuffer;

    private RawRequest request;
    private RawResponse response;

    private Adapter adapter;

    private boolean keepAlive = true;
    private boolean error = false;
    
    /** 一个长连接最多处理多少个 Request，-1 表示不限制 */
    private int maxKeepAliveRequests = -1;
    
    public HttpProcessor() {
        request = new RawRequest();
        inBuffer = new InputBuffer(request);
        request.hook(this);
        
        response = new RawResponse();
        outBuffer = new OutputBuffer(response);
        response.hook(this);
        maxKeepAliveRequests = 10;
    }

    @Override
    public SocketState process(NioChannel socket) {
        inBuffer.setSocket(socket);
        outBuffer.setSocket(socket);
        
        int keepAliveLeft = maxKeepAliveRequests;
        
        while (!error && keepAlive) {
            // 1. 解析请求头
        	try {
        		if (!inBuffer.parseRequestLineAndHeaders()) {
        			return SocketState.LONG;
        		}
        	} catch (IOException e) {
        		// 这里异常通常是 连接关闭和 socket 超时，EOFException SocketTimeoutException
        		return SocketState.CLOSED;
        	}
        	// 2. 校验请求头数据，设置请求体解码器
    		prepareRequest();
    		
    		// 3. 检查是否还要保持连接
    		if (maxKeepAliveRequests > 0 && --keepAliveLeft == 0) {
    		    keepAlive = false;
    		}
    		// 4. 交给容器处理请求并生成响应
        	if (!error) {
        		try {
        		    log.debug("交给容器处理请求并生成响应");
        			adapter.service(request, response);
        		} catch (Exception e) {
        			error = true;
        			e.printStackTrace();
        		}
        	}
        	
        	try {
                inBuffer.end();
            } catch (Throwable t) {
                log.error("Error finishing request", t);
                error = true;
                response.setStatus(500);
            }
        	try {
        	    outBuffer.end();
        	} catch (Throwable t) {
        	    log.error("Error finishing response", t);
        	    error = true;
        	    response.setStatus(500);
        	}
        	
        	// 5. 回收释放资源处理下一个请求
        	inBuffer.recycle();
        	outBuffer.recycle();
        	
        	// 6. 返回保持连接的状态 
        	if (!error && keepAlive) {
        		return SocketState.OPEN;
        	}
        }// end while
        return SocketState.CLOSED;
    }
    /**
     * 检查请求头部值是否合法，设置请求体解码器
     */
    private void prepareRequest() {
        // 0. 检查协议版本
        String verion = request.getProtocol();
        if (!"HTTP/1.1".equalsIgnoreCase(verion)) {
            error = true;
//            Send 505; Unsupported HTTP version TODO
//            response.setStatus(505);
        }
        
        // 1. 检查是否要保持连接
        String conn = request.getHeader("connection");
        if (conn == null || "close".equals(conn)) {
            keepAlive = false;
        } else if ("keep-alive".equals(conn)) {
            keepAlive = true;
        }
        // 2. 检查 expect 头 TODO
        
        // 3. 检查传输编码
        boolean contentDelimitation = false;
        String transferEncoding = request.getHeader("transfer-encoding");
        if ("chunked".equals(transferEncoding)) {
            contentDelimitation = true;
            inBuffer.setBodyCodec(new ChunkedCodec());
        }
        
        // 4. 检查是否有content-length头
        int contentLength = request.getContentLength();
        if (contentLength >= 0) { 
            if (contentDelimitation) {
                // 有了 chunked 编码，contentLength 无效
                request.removeHeader("content-length");
                request.setContentLength(-1);
            } else {
                inBuffer.setBodyCodec(new IdentityCodec(contentLength));
                contentDelimitation = true;
            }
        }
        // 5. 检查 host
        String host = request.getHeader("host");
        if (host == null || host.length() <= 0) {
            error = true;
//            400 - Bad request TODO
//            response.setStatus(400);
        }
    }

    private void prepareResponse() throws IOException {
        // 0. 检查是否有响应体
        int statusCode = response.getStatus();
        if ((statusCode == 204) || (statusCode == 205)
            || (statusCode == 304)) {
            // No entity body
            response.setContentLength(-1);
        } else {
            // 1. Content-Type
            String contentType = response.getContentType();
            if (contentType != null) {
                response.addHeader("Content-Type", contentType);
            }
            String contentLanguage = response.getContentLanguage();
            if (contentLanguage != null) {
                response.addHeader("Content-Language", contentLanguage);
            }
            // 2. 设置响应体编码处理器
            int contentLength = response.getContentLength();
            if (contentLength != -1) {
                response.addHeader("Content-Length", String.valueOf(contentLength));
                outBuffer.setBodyCodec(new IdentityCodec(contentLength));
            } else {
                response.addHeader("Transfer-Encoding", "chunked");
                outBuffer.setBodyCodec(new ChunkedCodec());
            }
        }
        
        response.addHeader("Server", "RxTomcat/1.0");
    }
    
    @Override
    public void action(ActionCode actionCode, Object... param) {
        switch (actionCode) {
        case COMMIT:
            if (!response.isCommitted()) {
                try {
                    prepareResponse();
                    outBuffer.commit();
                } catch (IOException e) {
                    error = true;
                }
            }
            break;
        case CLOSE:
            action(ActionCode.COMMIT);
            try {
				outBuffer.end();
//				outBuffer.flush();
			} catch (IOException e) {
				error = true;
				e.printStackTrace();
			}
            break;
        case ACK:
            break;
        case PARSE_PARAMS:
            inBuffer.readAndParseBody();
            break;
        case READ_BODY:
            BufferHolder buffHolder = (BufferHolder)param[0];
            try {
                inBuffer.readBody(buffHolder);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            break;
        case WRITE_BODY:
            action(ActionCode.COMMIT);
            try {
                outBuffer.writeBody((ByteBuffer)param[0]);
            } catch (IOException e) {
                error = true;
                e.printStackTrace();
            }
            break;
        case FLUSH:
            action(ActionCode.COMMIT);
            try {
                outBuffer.flush();
            } catch (IOException e) {
                error = true;
                e.printStackTrace();
            }
            break;
        default:
            break;
        }
    }
    
    public void setAdaptor(Adapter adapter) {
        this.adapter = adapter;
    }
}

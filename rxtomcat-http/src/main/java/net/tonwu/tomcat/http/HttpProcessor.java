package net.tonwu.tomcat.http;

import java.io.IOException;

import net.tonwu.tomcat.http.filters.ChunkedFilter;
import net.tonwu.tomcat.http.filters.IdentityFilter;
import net.tonwu.tomcat.net.Handler.SocketState;
import net.tonwu.tomcat.net.NioChannel;
import net.tonwu.tomcat.net.Processor;
import net.tonwu.tomcat.net.ServerInfo;

public class HttpProcessor implements Processor, ActionHook {

    private InputBuffer inBuffer;
    private OutputBuffer outBuffer;

    private RawRequest request;
    private RawResponse response;

    private Adapter adapter = new AdapterImpl();

    private boolean keepAlive = true;
    private boolean error;
    
    public HttpProcessor() {
        inBuffer = new InputBuffer();
        
        request = new RawRequest();
        request.setInputBuffer(inBuffer);
        request.hook(this);
        inBuffer.setRequest(request);
        
        outBuffer = new OutputBuffer();
        response = new RawResponse();
        outBuffer.setRawResponse(response);
        response.setOutputBuffer(outBuffer);
        response.hook(this);
    }

    @Override
    public SocketState process(NioChannel socket) {
        inBuffer.setSocket(socket);
        outBuffer.setSocket(socket);
        
        try {
            if (!inBuffer.parseRequestLineAndHeaders()) {
            	return SocketState.LONG;
            }
        } catch (IOException e) { 
            // 这里异常通常是 连接关闭和socket 超时，EOFException SocketTimeoutException
        	return SocketState.CLOSED;
        }
        
        prepareRequest();
        
        if (!error) {
            try {
				adapter.service(request, response);
			} catch (Exception e) {
				error = true;
				e.printStackTrace();
			}
        }
        keepAlive = false;
        if (!error && keepAlive) {
        	return SocketState.OPEN;
        }
        return SocketState.CLOSED;
    }
    /**
     * 检查请求头部值是否合法
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
        if ("close".equals(conn)) {
            keepAlive = false;
        } else if ("keep-alive".equals(conn)) {
            keepAlive = true;
        }
        // 2. 检查 expect 头 TODO
        
        // 3. 检查传输编码
        boolean contentDelimitation = false;
        String transferEncoding = request.getHeader("transfer-encoding");
        if (transferEncoding != null && "".equals(transferEncoding)) {
            contentDelimitation = true;
            inBuffer.setBodyFilter(new ChunkedFilter());
        }
        
        // 4. 检查是否有content-length头
        long contentLength = request.getContentLength();
        if (contentLength >= 0) { 
            if (contentDelimitation) {
                // 有了 chunked 编码，contentLength 无效
                request.removeHeader("content-length");
                request.setContentLength(-1);
            } else {
                inBuffer.setBodyFilter(new IdentityFilter());
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
            outBuffer.setBodyFilter(new IdentityFilter());
        } else {
            response.addHeader("Transfer-Encoding", "chunked");
            outBuffer.setBodyFilter(new ChunkedFilter());
        }
        
        response.addHeader("Server", "RxTomcat/1.0");
        
        // 3. 将状态行写入缓冲区
        outBuffer.writeStatus();
        // 4. 将响应头域写入缓冲区
        outBuffer.writeHeaders();
    }
    
    @Override
    public void action(ActionCode actionCode, Object... param) {
        switch (actionCode) {
        case COMMIT: {
            if (!response.isCommited()) {
                try {
                    prepareResponse();
                    outBuffer.commit();
                } catch (IOException e) {
                    error = true;
                }
            }
            break;
        }
        case CLOSE: {
            action(ActionCode.COMMIT);
            try {
				outBuffer.end();
//				outBuffer.flush();
			} catch (IOException e) {
				error = true;
				e.printStackTrace();
			}
            break;
        }
        case ACK: {
            break;
        }
        }
    }
}

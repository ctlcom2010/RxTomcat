package net.tonwu.tomcat.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import net.tonwu.tomcat.http.ActionHook.ActionCode;
import net.tonwu.tomcat.net.ServerInfo;

/**
 * 这里简单的返回请求信息和服务信息
 * @author wskwbog
 */
public class AdapterImpl implements Adapter {

	private RawRequest rawReq;
	private RawResponse rawResp;
	
    @Override
    public void service(RawRequest request, RawResponse response) throws IOException {
    	rawReq = request;
    	rawResp = response;
    	
    	rawReq.doRead();
    	
        StringBuilder content = new StringBuilder();
        content.append("Server version: " + "RxTomcat/1.1" + "\r\n");
        content.append("OS Name:        " + System.getProperty("os.name") + "\r\n");
        content.append("OS Version:     " + System.getProperty("os.version") + "\r\n");
        content.append("Architecture:   " + System.getProperty("os.arch") + "\r\n");
        content.append("JVM Version:    " + System.getProperty("java.runtime.version") + "\r\n");
        content.append("JVM Vendor:     " + System.getProperty("java.vm.vendor") + "\r\n\r\n");
        content.append(request.toString());
        byte[] bytes = content.toString().getBytes(StandardCharsets.UTF_8);
        
        rawResp.setContentType("text/plain");
//        rawResp.setContentLength(bytes.length);
        
        rawResp.doWrite(bytes);
        
        rawResp.action(ActionCode.CLOSE, null);
        
    }
}

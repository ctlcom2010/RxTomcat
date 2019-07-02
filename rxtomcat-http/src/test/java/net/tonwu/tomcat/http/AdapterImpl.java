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
import java.nio.charset.StandardCharsets;

import net.tonwu.tomcat.http.ActionHook.ActionCode;

/**
 * 这里简单的返回请求信息和服务信息
 * 
 * @author tonwu.net
 */
public class AdapterImpl implements Adapter {

	private RawRequest rawReq;
	private RawResponse rawResp;
	
    @Override
    public void service(RawRequest request, RawResponse response) throws IOException {
    	rawReq = request;
    	rawResp = response;
    	
    	// 触发请求体的读取和解析
    	rawReq.getParameters().get("none");
    	
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
//        rawResp.setContentLength(bytes.length); // identity-定长
        
        rawResp.doWrite(ByteBuffer.wrap(bytes));
        
        rawResp.action(ActionCode.CLOSE, null);
    }
}

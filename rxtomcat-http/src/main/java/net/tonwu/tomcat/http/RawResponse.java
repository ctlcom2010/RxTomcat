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
import java.util.HashMap;

import net.tonwu.tomcat.http.ActionHook.ActionCode;

/**
 * 原始的 Http 响应对象
 * 
 * @author tonwu.net
 */
public class RawResponse  implements Recyclable {
    
	/** 状态行和响应头域是否已经写入到发送缓冲区中 */
	private boolean committed = false;
	
	private int status = 200;
	private String message;
	private String contentType = null;
	private String contentLanguage = null;
	private String characterEncoding = "utf-8";
	private int contentLength = -1;
	private HashMap<String, String> headers = new HashMap<>();

	private ActionHook hook;
    public void hook(ActionHook hook) {
        this.hook = hook;
    }
    public void action(ActionCode action, Object param) {
        if (hook != null) {
            if (param == null)
                hook.action(action, this);
            else
                hook.action(action, param);
        }
    }
    /**
     * 写入响应体数据
     * 
     * @param bytes 响应体字节数据
     * @throws IOException
     */
    public void doWrite(ByteBuffer bytes) throws IOException {
        action(ActionCode.WRITE_BODY, bytes);
    }
    
	/**
	 * 没有对相同 header名称（如 Set-Cookie）进行处理
	 */
	public void addHeader(String name, String value) {
		headers.put(name, value);
	}
	
	public String getHeader(String name) {
		return headers.get(name);
	}

	/**
	 * 状态行和响应头域是否已经写入到发送缓冲区中
	 */
	public boolean isCommitted() {
		return committed;
	}
	public void setCommitted(boolean committed) {
		this.committed = committed;
	}
	
	public HashMap<String, String> headers() {
		return headers;
	}

	public int getStatus() {
		return status;
	}
	public void setStatus(int status) {
		this.status = status;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	
	public String getContentType() {
	    String retv = contentType;
        if (retv != null && characterEncoding != null) {
            retv = retv + ";charset=" + characterEncoding;
        }
	    
		return retv;
	}
	
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
	public String getContentLanguage() {
		return contentLanguage;
	}
	public void setContentLanguage(String contentLanguage) {
		this.contentLanguage = contentLanguage;
	}
	public String getCharacterEncoding() {
		return characterEncoding;
	}
	public void setCharacterEncoding(String characterEncoding) {
		this.characterEncoding = characterEncoding;
	}
	public int getContentLength() {
		return contentLength;
	}
	public void setContentLength(int contentLength) {
		this.contentLength = contentLength;
	}
	public void setHook(ActionHook hook) {
		this.hook = hook;
	}
	
    @Override
    public void recycle() {
        committed = false;
        contentType = null;
        contentLength = -1;
        headers.clear();
        status = 200;
        message = "";
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Response headers: \r\n");
        builder.append("HTTP/1.1 ");
        builder.append(status);
        builder.append(HttpToken.msg(status)).append("\r\n");
        return builder.toString();
    }
    
    public void reset() throws IllegalStateException {
        if (committed) {
            throw new IllegalStateException();
        }
        recycle();
    }
}

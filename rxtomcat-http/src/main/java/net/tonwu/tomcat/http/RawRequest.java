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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map.Entry;

import net.tonwu.tomcat.http.ActionHook.ActionCode;

/**
 * 原始的 Http 请求对象，包含请求方法、请求参数、uri、请求头域
 * 
 * @author tonwu.net
 */
public class RawRequest implements Recyclable {
	private String method; // GET POST ..
	private String uri; // /xxx.jsp
	
	private ByteBuffer query; // 存储原始字节，对特殊的参数处理
	private int queryStartPos = -1;
	
	private String protocol; // HTTP/1.1

	private String contentType;
	private int contentLength = -1;
	
	private boolean parametersParsed = false;
	private boolean parseParamFail = false;
	private HashMap<String, String> parameters = new HashMap<>();
	private HashMap<String, String> headers = new HashMap<>();

	private HashMap<String, Object> attributes = new HashMap<>();
	
	public String getHeader(String name) {
		return headers.get(name);
	}
	public void addHeader(String name, String value) {
		headers.put(name, value);
	}
	public String removeHeader(String name) {
		return headers.remove(name);
	}
	
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
    public boolean isParseParamFail() {
        return parseParamFail;
    }
    public void setParseParamFail(boolean parseParamFail) {
        this.parseParamFail = parseParamFail;
    }
    public HashMap<String, String> getParameters() {
        if (!parametersParsed) {
            action(ActionCode.PARSE_PARAMS, null);
        }
		return parameters;
	}
    
	public String getContentType() {
		if (contentType == null) {
		    // 有可能含有 ; charset=utf-8
			contentType = headers.get("content-type");
		}
		return contentType;
	}
	
    @Override
    public void recycle() {
        contentType = null;
        contentLength = -1;
        
        parametersParsed = false;
        parseParamFail = false;
        parameters.clear();
        headers.clear();
        attributes.clear();
        queryStartPos = -1;
        if (query != null) {
            query.clear();
            query = null;
        }
    }
    
    public Charset getEncoding() {
        String type = getContentType();
        if (type != null) {
            int start = type.indexOf("charset=");
            if (start >= 0) {
                String encoding = type.substring(start + 8);
                return Charset.forName(encoding.trim());
            }
        }
        return StandardCharsets.UTF_8; // 默认 utf-8 编码
    }
    
	public int getContentLength() {
		if (contentLength > 0) return contentLength;
		
		String v = getHeader("content-length");
		if (v != null) {
			return Integer.valueOf(v);
		}
		return -1;
	}
	
	// Getter&Setter
	public String getMethod() {
		return method;
	}
	public void setMethod(String method) {
		this.method = method;
	}
	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
	}
	public ByteBuffer getQuery() {
		return query;
	}
	public int getQueryStartPos() {
        return queryStartPos;
    }
    public void setQueryStartPos(int queryStartPos) {
        this.queryStartPos = queryStartPos;
    }
    public void setQuery(ByteBuffer query) {
        this.query = query;
    }
    public String getProtocol() {
		return protocol;
	}
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
	public void setContentLength(int contentLength) {
		this.contentLength = contentLength;
	}
    
	public boolean isParametersParsed() {
		return parametersParsed;
	}
	public void setParametersParsed(boolean parametersParsed) {
		this.parametersParsed = parametersParsed;
	}
    public void setAttribute(String name, Object o) {
        attributes.put(name, o);
    }
    public HashMap<String, Object> getAttributes() {
        return attributes;
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Request headers: \r\n");
        builder.append(method).append(" ");
        builder.append(uri);
        if (query != null) {
            query.reset();
            builder.append(new String(query.array(),0, query.remaining()));
        }
        builder.append(" ").append(protocol).append("\r\n");
        for (Entry<String, String> header : headers.entrySet()) {
            builder.append(header.getKey()).append(":").append(header.getValue()).append("\r\n");
        }
        builder.append("\r\n");
        for (Entry<String, String> param : parameters.entrySet()) {
            builder.append(param.getKey()).append("=").append(param.getValue()).append("\r\n");
        }
        
        return builder.toString();
    }
}

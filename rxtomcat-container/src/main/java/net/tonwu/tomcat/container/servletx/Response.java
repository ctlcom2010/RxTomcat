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
import java.io.PrintWriter;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import net.tonwu.tomcat.http.RawResponse;
import net.tonwu.tomcat.http.Recyclable;

/**
 * 这个对象最终会传到 Servlet 的 service 方法中，主要功能：<br>
 * <p>
 *  - 支持设置 Cookie<br>
 *  - 支持 PrintWriter 和 ServletOutStream 字符和字节输出流<br>
 *  
 * @author tonwu.net
 */
public class Response implements HttpServletResponse, Recyclable {
	private RawResponse rawResp;
	
	private Request containerRequest;
	
	private AppOutputBuffer obuffer; 
	private PrintWriter writer;
	
	private boolean error = false;
	
	private boolean usingOutputStream = false;
	private boolean usingWriter = false;
	
	@Override
	public boolean isCommitted() {
		return rawResp.isCommitted();
	}
	
    @Override
	public void addCookie(Cookie cookie) {
		StringBuilder buf = new StringBuilder();
		
		String prefix = cookie.getName() + "=";
		String value = cookie.getValue();
		if (value == null || value.length() == 0) {
			value ="\"\"";
		}
		boolean isSet = false;
		String cookies = rawResp.getHeader("Set-Cookie");
		if (cookies != null) {
			if (cookies.contains(prefix)) {
				isSet = true;
				for (String tmp : cookies.split(";")) {
					if (tmp.contains(prefix)) {
						buf.append(prefix).append(value).append(";");
					} else {
						buf.append(tmp).append(";");
					}
				}
				buf.delete(buf.length() - 1, buf.length());
			} else {
				buf.append(cookies).append(";");
			}
		}
		if (!isSet) {
			buf.append(prefix).append(value);
		}
		
		rawResp.addHeader("Set-Cookie", buf.toString());
	}
	
	public void finish() throws IOException {
	    obuffer.close();
	}
	
    @Override
    public void sendRedirect(String location) throws IOException {
        StringBuffer sb = new StringBuffer();
        if (!(location.startsWith("http:") || location.startsWith("https:"))) {
            // 转成绝对路径
            
            sb.append("http://").append(containerRequest.getHeader("host"));
            
            if (location.startsWith("/")) {
                sb.append(location);
            } else {
                sb.append(containerRequest.getRequestURI()).append("/").append(location);
            }
        }
        String absolute = sb.toString();
        setStatus(SC_FOUND); // 302
        setHeader("Location", absolute);
        obuffer.setSuspended(true);
    }
	
    public void setSuspended(boolean suspended) {
        obuffer.setSuspended(suspended);
    }
    
    @Override
    public void sendError(int sc) throws IOException {
        sendError(sc, null);
    }
    @Override
    public void sendError(int sc, String msg) throws IOException {
        if (isCommitted()) {
            throw new IllegalStateException("Cannot call sendError() after the response has been committed");
        }
        
        setStatus(sc);
        
        error = true;
        
        rawResp.setStatus(sc);
        rawResp.setMessage(msg);
        
        // 清空已经缓存的数据
        obuffer.recycle();
        
        // 响应，没有响应体数据
        setSuspended(true);
    }
    
    public boolean isError() {
        return error;
    }
	
	@Override
	public void recycle() {
	    obuffer.recycle();
	    usingOutputStream = false;
	    usingWriter = false;
	    error = false;
	}
	
	@Override
	public PrintWriter getWriter() throws IOException {
	    if (usingOutputStream) {
            throw new IllegalStateException("getOutputStream() has already been called for this response");
        }
	    usingWriter = true;
	    
	    if (writer == null) {
            writer = new PrintWriter(new RespWriter(obuffer));
        }
        return writer;
	}
	
	public PrintWriter getReporter() throws IOException {
        if (obuffer.isNew()) {
            if (writer == null) {
                writer = new PrintWriter(new RespWriter(obuffer));
            }
            return writer;
        } else {
            return null;
        }
    }
	
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (usingWriter) {
            throw new IllegalStateException("getWriter() has already been called for this response");
        }
        usingOutputStream = true;
        return obuffer;
    }
	
	@Override
	public void setStatus(int sc) {
		rawResp.setStatus(sc);
	}
	
	@Override
	public void setContentType(String type) {
		rawResp.setContentType(type);
	}
	
	@Override
	public void setContentLength(int len) {
		rawResp.setContentLength(len);
	}
	
	public void setRawResp(RawResponse rawResp) {
		this.rawResp = rawResp;
		if (obuffer == null) {
		    obuffer = new AppOutputBuffer(rawResp);
		}
	}
	
	@Override
	public void setCharacterEncoding(String charset) {
		rawResp.setCharacterEncoding(charset);
	}
	
	@Override
	public String getCharacterEncoding() {
	    return rawResp.getCharacterEncoding();
	}
	
    @Override
    public void setHeader(String name, String value) {
        addHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        rawResp.addHeader(name, value);
    }
	
	/* 以下方法未实现 TODO */
	@Override
	public String getContentType() {
		return null;
	}

	@Override
	public void setBufferSize(int size) {
	}

	@Override
	public int getBufferSize() {
		return 0;
	}

	@Override
	public void flushBuffer() throws IOException {
	}

	@Override
	public void resetBuffer() {
	}

	@Override
	public void reset() {
	    rawResp.reset();
	    recycle();
	}

	@Override
	public void setLocale(Locale loc) {
	}

	@Override
	public Locale getLocale() {
		return null;
	}

	@Override
	public boolean containsHeader(String name) {
		return false;
	}

	@Override
	public String encodeURL(String url) {
		return null;
	}

	@Override
	public String encodeRedirectURL(String url) {
		return null;
	}

	@Override
	public String encodeUrl(String url) {
		return null;
	}

	@Override
	public String encodeRedirectUrl(String url) {
		return null;
	}

	@Override
	public void setDateHeader(String name, long date) {
	}

	@Override
	public void addDateHeader(String name, long date) {
	}

	@Override
	public void setIntHeader(String name, int value) {
	}

	@Override
	public void addIntHeader(String name, int value) {
	}

	@Override
	public void setStatus(int sc, String sm) {
	}

    public int getContentLength() {
        return rawResp.getContentLength();
    }

    public String getMessage() {
        return rawResp.getMessage();
    }

    public int getStatus() {
        return rawResp.getStatus();
    }

    @Override
    public String toString() {
        return rawResp.toString();
    }

    public void setContainerRequest(Request containerRequest) {
       this.containerRequest = containerRequest;
    }
}

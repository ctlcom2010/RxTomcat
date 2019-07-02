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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import net.tonwu.tomcat.container.core.Context;
import net.tonwu.tomcat.container.core.Wrapper;
import net.tonwu.tomcat.container.session.Manager;
import net.tonwu.tomcat.container.session.Session;
import net.tonwu.tomcat.http.RawRequest;
import net.tonwu.tomcat.http.Recyclable;

/**
 * 这个对象最终会传到 Servlet 的 service 方法中，主要功能：<br>
 * <p>
 *  - 对 Session、Cookie 和 请求参数的处理进行了实现<br>
 *  - 支持 ServletInputStream 输入流<br>
 *  
 * @author tonwu.net
 */
public class Request implements HttpServletRequest, Recyclable {
	private Response resp;
	
	// URL 映射结果
	private Context context;
	private Wrapper wrapper;
	
	private RawRequest rawReq;
	
	private SimpleDateFormat formats[] = {
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
    };
	
	protected Cookie[] cookies = null;
	@Override
	public Cookie[] getCookies() {
		if (cookies == null) {
			parseCookies();
		}
		return cookies;
	}
	private void parseCookies() {
		String cookiesStr = rawReq.getHeader("cookie");
		if (cookiesStr != null) {
			String[] cookiesArry = cookiesStr.split(";");
			int idx = cookiesArry.length;
			if (idx == 0) {
				return;
			}
			cookies = new Cookie[idx];
			for (int i = 0; i < idx; i++) {
				String[] temp = cookiesArry[i].trim().split("=");
				Cookie cookie = new Cookie(temp[0],temp[1]);
				cookies[i] = cookie;
			}
		}
	}
	
	@Override
	public String getParameter(String name) {
	    // 请求参数的解析放到了 RawRequest 中
		return rawReq.getParameters().get(name);
	}
	
	private Session session = null;
	private String sessionId = null;
	
    @Override
    public HttpSession getSession(boolean create) {

        if (session != null) {
            if (!session.isValid()) {
                session = null;
            } else {
                return session.getSession();
            }
        }
        
        Manager manager = getContext().getManager();
        if (sessionId != null) {
            session = manager.findSession(sessionId);
            if (session != null) {
                if (session.isValid()) {
                    session.access();
                    return session.getSession();
                } else {
                    session = null;
                }
            }
        }
        
        if (create) {
            session = manager.createSession();
            if (session == null) return null;
            
            // Add cookie
            Cookie cookie = new Cookie("JSESSIONID", session.getId());
            resp.addCookie(cookie);
            
            session.access();
            return session.getSession();
        } else {
            return null;
        }
    }
	
	@Override
	public HttpSession getSession() {
	    return getSession(true);
	}
	
	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}
	
	@Override
	public String getMethod() {
		return rawReq.getMethod();
	}
	
	@Override
	public String getRequestURI() {
		return rawReq.getUri();
	}
	
	@Override
	public ServletInputStream getInputStream() throws IOException {
		return null;
	}
	
	@Override
	public void recycle() {
		context = null;
		wrapper = null;
		
		cookies = null;
		if (session != null) {
			session.endAccess();
		}
		session = null;
	}
	
	public void setResp(Response resp) {
		this.resp = resp;
	}
	// Getter & Setter
	public Context getContext() {
		return context;
	}
	public void setContext(Context context) {
		this.context = context;
	}
	public Wrapper getWrapper() {
		return wrapper;
	}
	public void setWrapper(Wrapper wrapper) {
		this.wrapper = wrapper;
	}
	public void setRawReq(RawRequest rawReq) {
		this.rawReq = rawReq;
	}
	
    @Override
    public String getServletPath() {
        return wrapper.getWrapperPath();
    }
    
    @Override
    public void setAttribute(String name, Object o) {
        rawReq.setAttribute(name, o);
    }
    
    @Override
    public Object getAttribute(String name) {
        return rawReq.getAttributes().get(name);
    }
	
    /* 以下方法未实现 TODO */
	@Override
	public Enumeration<?> getAttributeNames() {
		return null;
	}

	@Override
	public String getCharacterEncoding() {
		return null;
	}

	@Override
	public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
	}

	@Override
	public int getContentLength() {
		return rawReq.getContentLength();
	}

	@Override
	public String getContentType() {
		return rawReq.getContentType();
	}

	@Override
	public Enumeration<?> getParameterNames() {
		return Collections.enumeration(rawReq.getParameters().keySet());
	}

	@Override
	public String[] getParameterValues(String name) {
		return null;
	}

	@Override
	public Map<?, ?> getParameterMap() {
		return null;
	}

	@Override
	public String getProtocol() {
		return null;
	}
	
	@Override
	public String getScheme() {
		return null;
	}

	@Override
	public String getServerName() {
		return null;
	}

	@Override
	public int getServerPort() {
		return 0;
	}

	@Override
	public BufferedReader getReader() throws IOException {
		return null;
	}

	@Override
	public String getRemoteAddr() {
		return null;
	}

	@Override
	public String getRemoteHost() {
		return null;
	}

	

	@Override
	public void removeAttribute(String name) {
	}

	@Override
	public Locale getLocale() {
		return null;
	}

	@Override
	public Enumeration<?> getLocales() {
		return null;
	}

	@Override
	public boolean isSecure() {
		return false;
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		return null;
	}

	@Override
	public String getRealPath(String path) {
		return null;
	}

	@Override
	public int getRemotePort() {
		return 0;
	}

	@Override
	public String getLocalName() {
		return null;
	}

	@Override
	public String getLocalAddr() {
		return null;
	}

	@Override
	public int getLocalPort() {
		return 0;
	}

	@Override
	public String getAuthType() {
		return null;
	}

	@Override
	public long getDateHeader(String name) {
	    String value = getHeader(name);
	    if (value != null) {
	        Date date = null;
	        for (int i = 0; (date == null) && (i < formats.length); i++) {
	            try {
	                date = formats[i].parse(value);
	            } catch (ParseException e) { }
	        }
	        if (date != null) {
	            return date.getTime();
	        }
	    }
		return -1;
	}

	@Override
	public String getHeader(String name) {
		return rawReq.getHeader(name);
	}

	@Override
	public Enumeration<?> getHeaders(String name) {
		return null;
	}

	@Override
	public Enumeration<?> getHeaderNames() {
		return null;
	}

	@Override
	public int getIntHeader(String name) {
		return 0;
	}

	@Override
	public String getPathInfo() {
		return null;
	}

	@Override
	public String getPathTranslated() {
		return null;
	}

	@Override
	public String getContextPath() {
		return "/" + context.getDocBase();
	}

	@Override
	public String getQueryString() {
		return null;
	}

	@Override
	public String getRemoteUser() {
		return null;
	}

	@Override
	public boolean isUserInRole(String role) {
		return false;
	}

	@Override
	public Principal getUserPrincipal() {
		return null;
	}

	@Override
	public String getRequestedSessionId() {
		return sessionId;
	}

	@Override
	public StringBuffer getRequestURL() {
		return null;
	}

	@Override
	public boolean isRequestedSessionIdValid() {
		return false;
	}

	@Override
	public boolean isRequestedSessionIdFromCookie() {
		return false;
	}

	@Override
	public boolean isRequestedSessionIdFromURL() {
		return false;
	}

	@Override
	public boolean isRequestedSessionIdFromUrl() {
		return false;
	}
	
    @Override
    public String toString() {
        return rawReq.toString();
    }
}

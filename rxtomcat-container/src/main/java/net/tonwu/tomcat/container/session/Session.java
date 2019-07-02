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
package net.tonwu.tomcat.container.session;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

/**
 * 简单实现  HttpSession
 * 
 * @author tonwu.net
 */
@SuppressWarnings("deprecation")
public class Session implements HttpSession, Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private String id;
	private long creationTime = 0L;
	private int maxInactiveInterval = -1;
	private transient long lastAccessedTime = 0L;
	
	private transient Manager manager = null;
	
	private volatile boolean isValid = false;
	private boolean isNew;
	
	private Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();

	public void access() {
		lastAccessedTime = System.currentTimeMillis();
	}

	public void endAccess() {
		isNew = false;
		lastAccessedTime = System.currentTimeMillis();
	}

	public void expire() {
		setValid(false);
		attributes.clear();
		manager.remove(getId());
	}
	
	public HttpSession getSession() {
		return this;
	}
	@Override
	public Object getAttribute(String name) {
		return attributes.get(name);
	}
	@Override
	public void removeAttribute(String name) {
		attributes.remove(name);
	}
	@Override
	public void setAttribute(String name, Object value) {
	    if (value == null) {
	        removeAttribute(name); // avoid NPE
//	        return;
	    }
		attributes.put(name, value);
	}

	@Override
	public void invalidate() {
		expire();
	}

	@Override
	public String toString() {
		return "Session@" + id + " [attributes=" + attributes + "]";
	}

	public void setManager(Manager manager) {
		this.manager = manager;
	}
	public Manager getManager() {
		return manager;
	}
	// Getter & Setter
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}

	public boolean isValid() {
		if (!isValid) return false;

		if (maxInactiveInterval > 0) {
			long now = System.currentTimeMillis();
			int timeIdle = (int) ((now - lastAccessedTime) / 1000L);
			if (timeIdle >= maxInactiveInterval) {
				expire();
			}
		}

		return isValid;
	}
	public void setValid(boolean isValid) {
		this.isValid = isValid;
	}
	
	@Override
	public long getCreationTime() {
		return creationTime;
	}
	@Override
	public long getLastAccessedTime() {
		return lastAccessedTime;
	}
	public void setCreationTime(long creationTime) {
		this.creationTime = creationTime;
		lastAccessedTime = creationTime;
	}
	// 
	@Override
	public void setMaxInactiveInterval(int interval) {
		maxInactiveInterval = interval;
	}
	
	
	@Override
	public void putValue(String arg0, Object arg1) {

	}
	@Override
	public void removeValue(String arg0) {

	}
	@Override
	public Enumeration<?> getAttributeNames() {
		return null;
	}
	@Override
	public int getMaxInactiveInterval() {
		return 0;
	}
	@Override
	public ServletContext getServletContext() {
		return null;
	}
	@Override
	public HttpSessionContext getSessionContext() {
		return null;
	}
	@Override
	public Object getValue(String arg0) {
		return null;
	}
	@Override
	public String[] getValueNames() {
		return null;
	}
	@Override
	public boolean isNew() {
		return isNew;
	}

}

package net.tonwu.tomcat.http;

import java.io.IOException;
import java.util.HashMap;

import net.tonwu.tomcat.http.ActionHook.ActionCode;

public class RawResponse {
    
	/** 状态行和响应头域是否已经写入到发送缓冲区中 */
	private boolean commited = false;
	
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
    private OutputBuffer outBuffer;
    public void doWrite(byte[] bytes) throws IOException {
        outBuffer.doWrite(bytes);
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
	public boolean isCommited() {
		return commited;
	}
	public void setCommited(boolean commited) {
		this.commited = commited;
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
		return contentType;
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
	public void setOutputBuffer(OutputBuffer outBuffer) {
	    this.outBuffer = outBuffer;
	}
}

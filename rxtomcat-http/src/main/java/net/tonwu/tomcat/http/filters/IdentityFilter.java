package net.tonwu.tomcat.http.filters;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.tonwu.tomcat.http.BodyFilter;
import net.tonwu.tomcat.http.InputBuffer;
import net.tonwu.tomcat.http.OutputBuffer;
import net.tonwu.tomcat.http.RawRequest;
import net.tonwu.tomcat.http.RawResponse;

/**
 * content-length 定长解码和编码
 * @author wskwbog
 */
public class IdentityFilter implements BodyFilter {
	private Object buffer;
	private int contentLength = -1;
	private int remaining;
	
    @Override
    public int doRead() throws IOException {
    	InputBuffer inBuffer = (InputBuffer) buffer;
    	int result = -1;
    	if (contentLength > 0 && remaining > 0) {
    		int n = inBuffer.readBody();
    		if (n > remaining) { // 读太多了
    			result = remaining;
    		} else {
                result = n;
            }
            if (n > 0) {
                remaining = remaining - n;
            }
    	}
        return result;
    }

    @Override
    public void doWrite(ByteBuffer src) throws IOException {
    	OutputBuffer outBuffer = (OutputBuffer) buffer;
    	outBuffer.write(src, false);
    }

	@Override
	public void setBuffer(Object inputBuffer) {
		buffer = inputBuffer;
	}

	@Override
	public void setRequest(RawRequest req) {
		contentLength = req.getContentLength();
		remaining = contentLength;
	}

	@Override
	public void setResponse(RawResponse resp) {
		contentLength = resp.getContentLength();
		remaining = contentLength;
	}

	@Override
	public void end() throws IOException {
	}
}

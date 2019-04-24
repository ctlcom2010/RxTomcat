package net.tonwu.tomcat.http;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface BodyFilter {

    public int doRead() throws IOException;

    public void doWrite(ByteBuffer src) throws IOException;

	public void setBuffer(Object inputBuffer);

	public void setRequest(RawRequest req);
	
	public void setResponse(RawResponse resp);

	public void end() throws IOException;

}

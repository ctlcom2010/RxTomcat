package net.tonwu.tomcat.http.filters;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.tonwu.tomcat.http.BodyFilter;
import net.tonwu.tomcat.http.Constants;
import net.tonwu.tomcat.http.OutputBuffer;
import net.tonwu.tomcat.http.RawRequest;
import net.tonwu.tomcat.http.RawResponse;

/**
 * chunked 动态长度编码和解码
 * @author wskwbog
 */
public class ChunkedFilter implements BodyFilter {
	private Object buffer;
	
	@Override
	public void doWrite(ByteBuffer src) throws IOException {
		OutputBuffer outBuffer = (OutputBuffer) buffer;
		int chunkLength = src.remaining();
		if (chunkLength <= 0) return;
		
		// chunk length 是 16 进制字符串 比如 489 -> 1e9 -> [0x31,0x65,0x39]
		String hex = Integer.toHexString(chunkLength).toLowerCase();
		
        // 1. 写入 chunk 长度
		outBuffer.write(hex.getBytes());
		outBuffer.write(Constants.CRLF);
        // 2. 写入 chunk data
		outBuffer.write(src);
		// 3. 写入 \r\n
		outBuffer.write(Constants.CRLF);
	}

	public void end() throws IOException {
		// 写入结束 chunked
		((OutputBuffer) buffer).write(Constants.END_CHUNK);
	}
	
    public static final byte[] HEX = 
    { (byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5', 
      (byte) '6', (byte) '7', (byte) '8', (byte) '9', (byte) 'a', (byte) 'b', 
      (byte) 'c', (byte) 'd', (byte) 'e', (byte) 'f' };


	@Override
	public void setBuffer(Object buffer) {
		this.buffer = buffer;
	}

	@Override
	public int doRead() throws IOException {
		return 0;
	}

	@Override
	public void setRequest(RawRequest req) {
	}
	@Override
	public void setResponse(RawResponse resp) {
	}
}

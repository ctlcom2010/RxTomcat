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
package net.tonwu.tomcat.http.codecs;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.tonwu.tomcat.http.BodyCodec;
import net.tonwu.tomcat.http.BufferHolder;
import net.tonwu.tomcat.http.InputBuffer;
import net.tonwu.tomcat.http.OutputBuffer;

/**
 * content-length 定长解码和编码
 * 
 * @author tonwu.net
 */
public class IdentityCodec implements BodyCodec {
    
	private int contentLength = -1; // 总长度
	private int remaining; // 剩余字节数
	
	public IdentityCodec(int length) {
	    contentLength = length;
	    remaining = length;
    }
	
    @Override
    public int doRead(InputBuffer input, BufferHolder buffHolder) throws IOException {
        int result = -1;
        if (contentLength > 0 && remaining > 0) {
            int n = input.realReadBytes(buffHolder);
            ByteBuffer view = buffHolder.getByteBuffer();
            if (n > remaining) {
                // 客户端发送的请求体数据太多了，为什么会这样？
                // 这里应该出现的几率不大，除非客户端发送的数据与请求头声明的不一致
                // 在超过最大限制时，chunked 传输时可能会多发送
                view.limit(view.position() + remaining);
                result = remaining;
            } else {
                result = n;
                remaining -= n;
            }
        }
        return result;
    }
    
    @Override
    public void endRead(InputBuffer input) throws IOException {
        int swallowed = 0;
        while (remaining > 0) {
            int n = input.readBody(null);
            if (n > 0) {
                swallowed += n;
                remaining -= n;
                if (swallowed > maxSwallowSize) {
                    throw new IOException("maxSwallowSize exceeded");
                }
            } else {
                remaining = 0;
            }
        }
    }

    @Override
    public void doWrite(OutputBuffer output, ByteBuffer src) throws IOException {
        // 定长写入比较简单，直接写就行
        output.write(src);
    }

    @Override
    public void endWrite(OutputBuffer output) throws IOException {
    }
}

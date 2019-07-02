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

/**
 * 请求体解码器、响应体编码器
 * 
 * @author tonwu.net
 */
public interface BodyCodec {
    
    int maxSwallowSize = 1 * 1024 * 1024; // 1MB
    
    /**
     * 读取 POST 请求体数据，实现了 chunked 和 identity 两种传输方式
     * 
     * @param input 关联的 HTTP 请求解析类
     * @param buffHolder 返回时它携带着实际请求体数据
     * @return -1 表示读取完毕，>=0 表示读到了数据
     * @throws IOException
     */
    public int doRead(InputBuffer input, BufferHolder buffHolder) throws IOException;

    /**
     * 如果服务端准备发送异常响应，但是请求体还有数据未读（比如当上传一个过大的文件时，服务端
     * 发现超过限制，但客户端仍在发送数据） 这个时候，为了让客户端能够接收到响应，服务端应该继
     * 续纯读取剩余的请求体数据，如果超过 maxSwallowSize 抛异常关闭连接
     * 
     * @param input 关联的 HTTP 请求解析类
     * @throws IOException 发生 IO 异常，关闭连接
     */
	public void endRead(InputBuffer input) throws IOException;
	
	/**
	 * 将响应体数据写入缓冲区，chunked 和 identity 写入方式不一样
	 * 
	 * @param output 关联的响应编码处理类
	 * @param src 待写入的数据
	 * @throws IOException
	 */
	public void doWrite(OutputBuffer output, ByteBuffer src) throws IOException;
	
	public void endWrite(OutputBuffer output) throws IOException;
}

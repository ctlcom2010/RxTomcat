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

/**
 * 在读取请求数据时，所有的缓冲区都是底层 NioChannel 中 ByteBuffer
 * 的视图，为了更方便的获取这个视图，提供这样的一个回调接口
 * 
 * @author tonwu.net
 */
public interface BufferHolder {
    
    /**
     * 设置读取结果数据的视图 ByteBuffer
     * 
     * @param buffer
     */
    public void setByteBuffer(ByteBuffer buffer);
    
    /**
     * 获取读取数据的视图 ByteBuffer
     */
    public ByteBuffer getByteBuffer();
}

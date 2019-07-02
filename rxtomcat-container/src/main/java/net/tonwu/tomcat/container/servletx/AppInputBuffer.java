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
import java.nio.ByteBuffer;

import javax.servlet.ServletInputStream;

import net.tonwu.tomcat.http.ActionHook.ActionCode;
import net.tonwu.tomcat.http.BufferHolder;
import net.tonwu.tomcat.http.RawRequest;

/**
 * 实现 ServletInputStream 从底层读取请求体数据
 * 
 * @author tonwu.net
 */
public class AppInputBuffer extends ServletInputStream implements BufferHolder {
    
    private RawRequest rawReq;
    private ByteBuffer bodyView;
    
    @Override
    public int read() throws IOException {
        return 0;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        rawReq.action(ActionCode.READ_BODY, this);
        if (bodyView == null) {
            return -1;
        }
        int n = Math.min(len, bodyView.remaining());
        bodyView.get(b, off, n);
        return n;
    }


    @Override
    public void setByteBuffer(ByteBuffer buffer) {
        bodyView = buffer;
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return bodyView;
    }
}

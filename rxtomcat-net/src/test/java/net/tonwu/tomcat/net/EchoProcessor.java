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
package net.tonwu.tomcat.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tonwu.tomcat.net.Handler.SocketState;

/**
 * 回显处理，用于测试，只在 Poller 上处理，没有模拟阻塞。它包含一个 main 方法，
 * 可直接运行进行测试，测试时，连接后可以先敲几次回车，因为如果是 Windows 的
 * cmd 首次输入可能看不见。
 * 
 * @author tonwu.net
 */
public class EchoProcessor implements Processor {
    final static Logger log = LoggerFactory.getLogger(EchoProcessor.class);
    
    public static void main(String[] args) {
        NioEndpoint endpoint = new NioEndpoint();
        try {
            endpoint.setHandler(new Handler() {
                @Override
                public Processor createProcessor() {
                    return new EchoProcessor();
                }
            });
            endpoint.init();
            endpoint.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static byte[] PROMPT = "NIO> ".getBytes();
    public static byte[] CRLF = "\r\n".getBytes();

    public static final int ETX = 3; // Ctrl+c
    public static final int BS = 8; // Backspace
    public static final int LF = 10; // 换行
    public static final int CR = 13; // 回车

    // 消息长度最大 8K
    private byte[] content = new byte[8192];
    private int pos;

    @Override
    public SocketState process(NioChannel ioChannel) {
        SelectionKey key = ioChannel.ioChannel().keyFor(ioChannel.getPoller().getSelector());
        try {
            if (key.isReadable()) {
                ByteBuffer rbuf = ioChannel.getReadBuffer();
                rbuf.clear();
                int n = ioChannel.read(rbuf);
                if (n > 0) {
                    rbuf.flip().limit(n);
                    while (rbuf.hasRemaining()) {
                        byte b = rbuf.get();
                        if (ETX == b) { // ctrl+c
                            return SocketState.CLOSED;
                        } else if (CR == b) { // \r
                        } else if (LF == b) { // \n
                            // 读取到了 \r\n 读取结束
                            log.info("  读取到 [\\r\\n] 读取结束，准备写入");
                            
                            return SocketState.WRITE;
                        } else if (BS == b) { // 退格键
                            if (pos > 0) {
                                content[pos--] = 0;
                            }
                        } else {
                            if (pos < content.length) {
                                content[pos++] = b;
                                
                                log.info("  读取[{}]，当前内容 [{}]", (char)b, new String(content, 0, pos));
                            }
                        }
                    }
                    return SocketState.LONG;
                } else if (n == 0) {
                    // 继续读取
                    return SocketState.LONG;
                } else {
                } // -1 socket close
            } else if (key.isWritable()) {
                ByteBuffer wbuf = ioChannel.getWriteBufffer();
                wbuf.clear();
                if (pos > 0) {
                    wbuf.put(content, 0, pos).put(CRLF);
                }
                wbuf.put(PROMPT).flip();
                
                log.info("回显内容 [{}], 显示提示符 [NIO> ]", pos > 0 ? new String(content, 0, pos) : "");
                
                ioChannel.write(wbuf);
                return SocketState.OPEN;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return SocketState.CLOSED;
    }
}

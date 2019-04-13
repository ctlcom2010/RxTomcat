package net.tonwu.tomcat.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import net.tonwu.tomcat.net.Handler.SocketState;

/**
 * 回显处理，只在 Poller 上处理，没有模拟阻塞
 * 
 * @author rxwheel.cc
 */
public class EchoProcessor implements Processor {

    public static byte[] PROMPT = "NIO> ".getBytes();
    public static byte[] CRLF = "\r\n".getBytes();

    public static final int ETX = 3; // Ctrl+c
    public static final int BS = 8; // Backspace
    public static final int LF = 10; // 换行
    public static final int CR = 13; // 回车

    private byte[] content = new byte[8192];
    private int pos;

    @Override
    public SocketState process(NioChannel ioChannel) {
        SelectionKey key = ioChannel.ioChannel().keyFor(ioChannel.getPoller().getSelector());
        try {
            if (key.isReadable()) {
                ByteBuffer rbuf = ioChannel.readBuf();
                rbuf.clear();
                int n = ioChannel.read(rbuf);
                if (n > 0) {
                    rbuf.flip().limit(n);
                    while (rbuf.hasRemaining()) {
                        byte b = rbuf.get();
                        if (ETX == b) { // ctrl+c
                            return SocketState.CLOSED;
                        } else if (CR == b) { // \r
                        } else if (LF == b) {
                            // 读取到了 \r\n 读取结束
                            return SocketState.WRITE;
                        } else if (BS == b) { // 退格键
                            if (pos > 0) {
                                content[pos--] = 0;
                            }
                        } else {
                            if (pos < content.length) {
                                content[pos++] = b;
                            }
                        }
                    }
                    return SocketState.LONG;
                } else if (n == 0) {
                    // 继续读取
                    return SocketState.LONG;
                }
            } else if (key.isWritable()) {
                ByteBuffer wbuf = ioChannel.writeBuf();
                wbuf.clear();
                if (pos > 0) {
                    wbuf.put(content, 0, pos).put(CRLF);
                }
                wbuf.put(PROMPT).flip();
                ioChannel.write(wbuf);
                return SocketState.OPEN;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return SocketState.CLOSED;
    }

    public static ByteBuffer usage() {
        StringBuilder builder = new StringBuilder();
        builder.append("==========================================\r\n");
        builder.append("  Tomcat NIO Test - EchoProcessor\r\n");
        builder.append("  Author: wskwbog\r\n");
        builder.append("  Git: http://github.com/tonwu/rxtomcat\r\n");
        builder.append("  Support: Ctrl+c Backspace\r\n");
        builder.append("==========================================\r\n");
        builder.append("NIO> ");
        return ByteBuffer.wrap(builder.toString().getBytes());
    }
}

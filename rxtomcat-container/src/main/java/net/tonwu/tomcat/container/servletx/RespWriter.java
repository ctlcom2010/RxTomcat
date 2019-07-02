package net.tonwu.tomcat.container.servletx;

import java.io.IOException;
import java.io.Writer;

/**
 * 提供直接写入字符串 Writer 的实现
 * 
 * @author tonwu.net
 */
public class RespWriter extends Writer {
    private AppOutputBuffer out;
    
    public RespWriter(AppOutputBuffer out) {
        this.out = out;
    }
    
    @Override
    public void write(int c) throws IOException {
        out.writeChar((char) c);
    }


    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        out.write(cbuf, off, len);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}

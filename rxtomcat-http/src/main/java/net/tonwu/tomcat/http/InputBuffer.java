package net.tonwu.tomcat.http;

import static net.tonwu.tomcat.http.Constants.COLON;
import static net.tonwu.tomcat.http.Constants.CR;
import static net.tonwu.tomcat.http.Constants.LF;
import static net.tonwu.tomcat.http.Constants.QUESTION;
import static net.tonwu.tomcat.http.Constants.SP;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CountDownLatch;

import net.tonwu.tomcat.net.NioChannel;

/**
 * 使用有限状态机解析 HTTP 协议请求行和请求体
 * 
 * @author wskwbog
 */
public class InputBuffer {
    // 请求头解析状态
    private ParseStatus status;

    public enum ParseStatus {
        METHOD, // 解析请求方法
        URI, // 解析请求 URI
        VERSION, // 解析协议版本
        QUERY, // 解析查询参数
        HEADER_NAME, // 解析头域名称
        HEADER_VALUE, // 解析头域值
        HEADER_END, // 解析一个头域完毕
        DONE // 解析完成
    }

    /** 正在解析请求头域 */
    private boolean parsingHeader = true;
    private int maxHeaderSize = 8192;

    private BodyFilter bodyFilter;
    private NioChannel socket;
    private RawRequest request;

    private byte[] headerBuffer; // 原始字节数据
    private int pos; // 当前可读位置
    private int lim; // 最大可读位置
    private int end; // 请求头信息在字节数组中结束的位置

    public InputBuffer() {
        status = ParseStatus.METHOD;
    }

    private StringBuilder sb = new StringBuilder();

    public String takeString() {
        String retv = sb.toString();
        sb.setLength(0);
        return retv;
    }

    /**
     * 使用状态机的方法，遍历字节解析请求头（解析时没有进行严谨性校验）
     * 
     * @param request
     * @return true - 读取完成，false - 读取到部分请求头
     * @throws IOException
     */
    public boolean parseRequestLineAndHeaders() throws IOException {
        String headerName = null;
        do {
            if (pos >= lim) {
                if (!readIfNeed(false)) {
                    return false; // 请求头不完整
                }
            }

            byte chr = headerBuffer[pos++];

            switch (status) {
            case METHOD:
                if (chr == SP) {
                    request.setMethod(takeString());
                    sb.setLength(0);
                    status = ParseStatus.URI;
                } else {
                    sb.append((char) chr);
                }
                break;
            case URI:
                if (chr == SP || chr == QUESTION) {
                    request.setUri(takeString());
                    sb.setLength(0);
                    if (chr == QUESTION) {
                        status = ParseStatus.QUERY;
                    } else {
                        status = ParseStatus.VERSION;
                    }
                } else {
                    sb.append((char) chr);
                }
                break;
            case QUERY: // 查询字符串特殊字符会被编码
                if (chr == SP) {
                    status = ParseStatus.VERSION;
                } else {
                    if (request.getQuery() == null) {
                        request.setQuery(ByteBuffer.allocate(512));
                    }
                    request.getQuery().put(chr);
                }
                break;
            case VERSION:
                if (chr == CR) {
                } else if (chr == LF) {
                    request.setProtocol(takeString());
                    status = ParseStatus.HEADER_NAME;
                } else {
                    sb.append((char) chr);
                }
                break;
            case HEADER_NAME:
                if (chr == COLON) {
                    headerName = takeString().toLowerCase();
                    status = ParseStatus.HEADER_VALUE;
                } else {
                    sb.append((char) chr);
                }
                break;
            case HEADER_VALUE:
                if (chr == CR) {
                } else if (chr == LF) {
                    // 这里有个空格没处理 ，trim 一下，也可以多加一个 HEADER_VALUE_START 状态来跳过空格
                    request.addHeader(headerName, takeString().trim().toLowerCase());
                    headerName = null;
                    status = ParseStatus.HEADER_END;
                } else {
                    sb.append((char) chr);
                }
                break;
            case HEADER_END:
                if (chr == CR) {
                } else if (chr == LF) {
                    status = ParseStatus.DONE;
                    parsingHeader = false;
                    end = pos;
                } else {
                    sb.append((char) chr);
                    status = ParseStatus.HEADER_NAME;
                }
                break;
            default:
                break;
            }

        } while (status != ParseStatus.DONE);
        return true;
    }

    /**
     * GET 请求参数在 URL 上直接能读取，而 POST 请求参数，在请求体中，通常有 chunked 和 identity 两种编码方式
     * 
     * @param len
     * @param offset
     * @throws IOException
     */
    public int doRead(byte[] body) throws IOException {
        int read = bodyFilter.doRead();
        if (read > 0) {
            System.arraycopy(headerBuffer, pos, body, 0, read);
        }
        pos = lim;
        return read;
    }

    public int readBody() throws IOException {
        if (pos >= lim) {
            if (!readIfNeed(true)) {
                return -1;
            }
        }
        int length = lim - pos;
        return length;
    }

    /**
     * 从通道读取字节
     * 
     * @return true - 有数据读取，false - 无数据可读
     * @throws IOException
     *             - 通道已经关闭，继续读取时发生
     * @throws IllegalArgumentException
     *             - 请求头太大，超过 8192KB
     * @throws EOFException
     *             - 连接被关闭
     */
    public boolean readIfNeed(boolean block) throws IOException {
        ByteBuffer bb = socket.readBuf();
        bb.clear();
        int n = -1;
        if (block) {
        	while (n == 0) {
        		n = socket.read(bb);
                if (n == -1) throw new EOFException();
                if (n > 0) break;
        	}
        	socket.readLatch = new CountDownLatch(1);
    		// 注册 Pooler 写事件
    		socket.getPoller().register(socket, SelectionKey.OP_READ);
    		try {
    			// 阻塞等待可写
				socket.readLatch.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    		socket.readLatch = null;
        	
        } else {
            n = socket.read(bb); // 可能返回 0
            if (n > 0) {
                if (parsingHeader) {
                    if (pos + n > maxHeaderSize) {
                        throw new IllegalArgumentException("请求头太大");
                    }
                } else {
                    pos = end;
                }
                bb.flip();
                bb.get(headerBuffer, pos, n);
                lim = pos + n;
            } else if (n == -1) {
                throw new EOFException("EOF，通道被关闭");
            }
        }
        return n > 0;
    }

    public void setSocket(NioChannel socket) {
        this.socket = socket;
        if (headerBuffer == null) {
            headerBuffer = new byte[maxHeaderSize + socket.readBuf().capacity()];
        }
    }

    public void setBodyFilter(BodyFilter body) {
        this.bodyFilter = body;
        body.setBuffer(this);
        body.setRequest(request);
    }

    public void setRequest(RawRequest req) {
        request = req;
    }

    public int available() {
        return lim - end;
    }

    /**
     * 解析请求参数，查询参数，查询参数可以是这样 a=%E5%88%9B+a
     */
    public void parseParameters(byte[] body) {
        // 第一步去除 %
        int len = body.length;
        for (int i = 0; i < len; i++) {
            byte b = body[i];
            if (b == '%') {
                // 把十六进制字符串替换成 int 值
                byte b1 = body[i + 1];
                byte b2 = body[i + 2];
                // b1 & 0xDF 转为大写字母
                int digitb1 = (b1 >= 'A') ? ((b1 & 0xDF) - 'A') + 10 : (b1 - '0');
                int digitb2 = (b2 >= 'A') ? ((b2 & 0xDF) - 'A') + 10 : (b2 - '0');
                // 转为 int 保存在 i 位置上，把后面两位字节去除
                body[i] = (byte) (digitb1 << 4 | digitb2); // 相当于 digitb1*16+digitb2
                System.arraycopy(body, i + 3, body, i + 1, len - i - 3);
                len -= 2;
            }
        }
        // 此时的参数就能使用字符集转为正确的字符串
        String params = new String(body, 0, len, request.getEncoding());
        String[] param = params.split("&");
        if (param != null && param.length > 0) {
            for (String ele : param) {
                if (ele.startsWith("=")) {
                    continue; // no name
                }
                try {
                    String[] nv = ele.trim().split("=");
                    String name = nv[0];
                    String value = nv.length > 1 ? nv[1] : null;
                    request.getParameters().put(name, value);
                } catch (Exception ignore) {
                }
            }
        }
    }
}

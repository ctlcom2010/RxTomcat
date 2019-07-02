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
import net.tonwu.tomcat.http.HttpToken;
import net.tonwu.tomcat.http.InputBuffer;
import net.tonwu.tomcat.http.OutputBuffer;

/**
 * chunked 动态长度编码和解码
 * 
 * @author tonwu.net
 */
public class ChunkedCodec implements BodyCodec, BufferHolder {
    
    /** 整个 chunk 块缓冲区视图 */
    private ByteBuffer chunkView;
    
    /** chunk 数据块长度 */
    private int chunkDataLength = 0;
    
    /** 是否是最后一个 chunk 块 */
    private boolean endChunk = false;
    
    /** 解析 chunk header 前是否要跳过前面的 \r\n */
    private boolean skipCrlfLazy = false;
    
    @Override
    public int doRead(InputBuffer input, BufferHolder buffHolder) throws IOException {
        if (endChunk) return -1;
        
        // 是否需要跳过 \r\n
        if (skipCrlfLazy) {
            skipCrlfLazy = false;
            skipCrlf(input);
        }
        
        // 1. 解析 chunk header 获取 chunk-data 长度
        if (chunkDataLength <= 0) {
            if((chunkDataLength = parseChunkHeader(input)) < 0) {
                throw new IOException("Invalid chunk header");
            }
            // 是否是最后一个块
            if (endChunk) return -1;
        }
        // 2. 读取实际数据，长度是 chunkDataLength
        if (readIfNeed(input) <= 0) {
            throw new IOException("Unexpected end of stream while reading request body");
        }
        
        int retv = 0;
        int remaining = chunkView.remaining();
        if (remaining < chunkDataLength) {
            // 记录实际请求数据
            buffHolder.setByteBuffer(chunkView.duplicate());
            
            // chunkView 跳过已读的 chunk data
            chunkView.position(chunkView.position() + remaining);
            retv = remaining;
            chunkDataLength -= remaining;
        } else {
            // 记录实际请求数据
            buffHolder.setByteBuffer(chunkView.duplicate());
            buffHolder.getByteBuffer().limit(chunkView.position() + chunkDataLength);
            
            // chunkView 跳过已读的 chunk data
            chunkView.position(chunkView.position() + chunkDataLength);
            retv = chunkDataLength;
            chunkDataLength = 0;
            
            // 3. 读取 \r\n 为下一个 chunk 块的解析最准备，如果缓冲区有足够的数据可读，就立即解析，否则延迟解析，把这个操作
            //    放到此方法的开头，这样做主要是因为阻塞
            if (chunkView.position() + 1 >= chunkView.limit()) {
                skipCrlfLazy = true;
            } else {
                skipCrlf(input);
            }
        }
        return retv;
    }
    
    /**
     * 移动缓冲区指针，跳过 \r\n
     */
    private void skipCrlf(InputBuffer input) throws IOException {
        boolean eol = false;
        while (!eol) {
            if (readIfNeed(input) <= 0) {
                throw new IOException("Unexpected end of stream while reading CRLF");
            }
            byte chr = chunkView.get();
            if (chr == HttpToken.CR) {
            } else if (chr == HttpToken.LF) {
                eol = true;
            }
        }
    }
    
    private int readIfNeed(InputBuffer input) throws IOException {
        if (chunkView == null || chunkView.position() >= chunkView.limit()) {
            if (input.realReadBytes(this) <= 0) {
                return -1; // 无效的块
            }
        }
        return chunkView.remaining();
    }
    
    /**
     * 解析 chunk header, 忽略 chunk 扩展选项，格式如下：
     * A10\r\n
     * F23;chunk-extension\r\n
     */
    private int parseChunkHeader(InputBuffer input) throws IOException {
        int result = 0;
        boolean eol = false,
                extension = false; // 遇到了扩展选项
        while (!eol) {
            // 读取必要的数据
            if (readIfNeed(input) <= 0) {
                return -1; // 无效的块
            }
            byte chr = chunkView.get();
            if (chr == HttpToken.CR) {
            } else if (chr == HttpToken.LF) {
                eol = true;
            } else if (chr == HttpToken.SEMI_COLON && !extension) {
                extension = true; // 遇到了冒号，后面数据就是扩展选项，这里忽略处理
            } else if (!extension) {
                // 读取 chunk-size，它是 16 进制数字字符串，小写字母
                //   '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'
                // 0x 30  31  32  33  34  35  36  37  38  39  61  62  63  64  65  66
                // 比如 15 -> f -> ['f'] -> [0x66]
                // 比如 489 -> 1e9 -> ['1','e','9'] -> [0x31,0x65,0x39]
                // 比如 4204 -> 106C -> ['1','0','6','c'] -> [0x31,0x30,0x36,0x63]
                // don't read data after the trailer
                // 获取字符对应的十进制数，比如 f 对应数字是 15
                int charValue = DEC[chr - '0'];
                if (charValue != -1) {
                    // 一个16进制数 4bit，左移4位合并低4位
                    // 相当于 result = result * 16 + charValue;
                    result = (result << 4) | charValue;
                } else {
                    // 非法字符
                    return -1;
                }
            }
        }
        
        if (result == 0) endChunk = true;
        
        return result;
    }
    
    @Override
    public void endRead(InputBuffer input) throws IOException {
        int swallowed = 0, read = 0;
        // 读取客户端发送的多余请求体数据，直到遇到一个 end chunk
        while ((read = input.readBody(null)) >= 0) {
            swallowed += read;
            if (swallowed > maxSwallowSize) {
                throw new IOException("maxSwallowSize exceeded");
            }
        }
    }

    /**
     * 十六进制字符转十进制数的数组，由 ASCII 表直接得来，DESC[char-'0']
     */
    private static final int[] DEC = {
        00, 01, 02, 03, 04, 05, 06, 07,  8,  9, -1, -1, -1, -1, -1, -1,
        -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, 10, 11, 12, 13, 14, 15,
    };
    
    @Override
    public void doWrite(OutputBuffer output, ByteBuffer src) throws IOException {
        // 写入 chunk 块
        int chunkLength = src.remaining();
        if (chunkLength <= 0) return;
        
        // chunk length 是 16 进制字符串 比如 489 -> 1e9 -> [0x31,0x65,0x39]
        String hex = Integer.toHexString(chunkLength).toLowerCase();
        
        // 1. 写入 chunk 长度
        output.write(hex.getBytes());
        output.write(HttpToken.CRLF);
        // 2. 写入 chunk data
        output.write(src);
        // 3. 写入 \r\n
        output.write(HttpToken.CRLF);
    }
    
    public void endWrite(OutputBuffer output) throws IOException {
        // 写入结束 chunked
        output.write(HttpToken.END_CHUNK);
    }

    @Override
    public void setByteBuffer(ByteBuffer buffer) {
        chunkView = buffer;
    }
    @Override
    public ByteBuffer getByteBuffer() {
        return chunkView;
    }
}

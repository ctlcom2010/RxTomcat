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
package net.tonwu.tomcat.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 字节数组功能类
 * 
 * @author tonwu.net
 */
public final class Bytes {
    private Bytes() { }

    public static byte[] int2bytes(int v) {
        return int2bytes(v, ByteOrder.BIG_ENDIAN);
    }

    public static byte[] int2bytes(int v, ByteOrder order) {
        byte[] b = new byte[4];
        if (order == ByteOrder.BIG_ENDIAN) {
            b[0] = (byte) (v >>> 24);
            b[1] = (byte) (v >>> 16);
            b[2] = (byte) (v >>>  8);
            b[3] = (byte) (v >>>  0);
        } else if (order == ByteOrder.LITTLE_ENDIAN) {
            b[0] = (byte) (v >>>  0);
            b[1] = (byte) (v >>>  8);
            b[2] = (byte) (v >>> 16);
            b[3] = (byte) (v >>> 24);
        }
        return b;
    }

    public static int bytes2int(byte[] b) {
        return bytes2int(b, ByteOrder.BIG_ENDIAN);
    }

    public static int bytes2int(byte[] b, ByteOrder order) {
        if (b.length != 4) {
            throw new IllegalArgumentException("The length of byte array must be 4");
        }
        return (int) bytes2long(b, order);
    }

    public static byte[] long2bytes(long v) {
        return long2bytes(v, ByteOrder.BIG_ENDIAN);
    }
    
    public static byte[] long2bytes(long v, ByteOrder order) {
        byte[] b = new byte[8];
        if (order == ByteOrder.BIG_ENDIAN) {
            b[0] = (byte) (v >>> 56);
            b[1] = (byte) (v >>> 48);
            b[2] = (byte) (v >>> 40);
            b[3] = (byte) (v >>> 32);
            b[4] = (byte) (v >>> 24);
            b[5] = (byte) (v >>> 16);
            b[6] = (byte) (v >>>  8);
            b[7] = (byte) (v >>>  0);
        } else if (order == ByteOrder.LITTLE_ENDIAN) {
            b[0] = (byte) (v >>>  0);
            b[1] = (byte) (v >>>  8);
            b[2] = (byte) (v >>> 16);
            b[3] = (byte) (v >>> 24);
            b[4] = (byte) (v >>> 32);
            b[5] = (byte) (v >>> 40);
            b[6] = (byte) (v >>> 48);
            b[7] = (byte) (v >>> 56);
        }
        return b;
    }
    
    public static long bytes2long(byte[] b) {
        return bytes2long(b, ByteOrder.BIG_ENDIAN);
    }
    
    public static long bytes2long(byte[] b, ByteOrder order) {
        long retval = 0;
        if (order == ByteOrder.BIG_ENDIAN) {
            for (int i = 0; i < b.length; i++) {
                retval += (b[i] & 0xFF) << ((b.length - i - 1) * 8);
            }
        } else if (order == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < b.length; i++) {
                retval += (b[i] & 0xFF) << (i * 8);
            }
        }
        return retval;
    }

    public static String bytes2Hex(byte[] b) {
        StringBuilder hexStr = new StringBuilder();
        for (int i = 1; i <= b.length; i++) {
            String hex = Integer.toHexString(b[i - 1] & 0xFF);
            if (hex.length() == 1) {
                hexStr.append('0');
            }
            hexStr.append(hex).append(" ");
            if (i % 16 == 0) {
                hexStr.append("\r\n");
            }
        }
        return hexStr.toString();
    }
    public static String bytes2Hex(ByteBuffer buff) {
        buff.flip();
        byte[] b = new byte[buff.remaining()];
        buff.get(b);
        return bytes2Hex(b);
    }
    public static String bytes2Hex(byte[] b, int offset, int length) {
        StringBuilder hexStr = new StringBuilder();
        for (int i = offset; i < length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hexStr.append('0');
            }
            hexStr.append(hex).append(" ");
            if (i % 16 == 0) {
                hexStr.append("\r\n");
            }
        }
        return hexStr.toString();
    }
}

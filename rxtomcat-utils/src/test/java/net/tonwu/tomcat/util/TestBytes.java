package net.tonwu.tomcat.util;

import java.nio.ByteOrder;

import org.junit.Assert;
import org.junit.Test;

public class TestBytes {
    
    @Test
    public void convert() {
        byte[] testCase = new byte[]{0,0,4,0}; // BIG_ENDIAN
        
        Assert.assertArrayEquals(testCase, Bytes.int2bytes(1024));
        Assert.assertTrue(1024 == Bytes.bytes2int(testCase));
        
        // 4 * 2^16 = 262144
        Assert.assertTrue(262144 == Bytes.bytes2int(testCase, ByteOrder.LITTLE_ENDIAN));
        
        Assert.assertTrue("00 00 04 00 ".equals(Bytes.bytes2Hex(testCase)));
    }
}

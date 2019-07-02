package net.tonwu.tomcat.http;

import java.io.IOException;

import net.tonwu.tomcat.net.Handler;
import net.tonwu.tomcat.net.NioEndpoint;
import net.tonwu.tomcat.net.Processor;
// curl 172.31.1.41:10393 -X POST -d "user=abc&passwd=创"
// 
/**
 * 使用 curl 工具进行测试：<br>
 * curl 172.31.1.41:10393/index?a=2 -X POST -d "user=abc&passwd=创" -v <br>
 * curl 172.31.1.41:10393 -X POST -H "Transfer-Encoding:chunked" -d "user=abc&passwd=创" -v <br>
 * curl 172.31.1.41:10393 -F "" -v <br>
 * @author tonwu.net
 */
public class TestHttpProcessor {
    public static void main(String[] args) {
        NioEndpoint endpoint = new NioEndpoint();
        endpoint.setHandler(new Handler() {
            @Override
            public Processor createProcessor() {
                HttpProcessor httpProcessor = new HttpProcessor();
                httpProcessor.setAdaptor(new AdapterImpl());
                return httpProcessor;
            }
        });
        try {
            endpoint.init();
            endpoint.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

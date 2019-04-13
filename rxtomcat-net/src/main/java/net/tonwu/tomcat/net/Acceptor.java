package net.tonwu.tomcat.net;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;

import net.tonwu.tomcat.util.log.Log;
import net.tonwu.tomcat.util.log.LoggerFactory;

public class Acceptor implements Runnable {
    final static Log log = LoggerFactory.getLogger(Acceptor.class);
    
    public static final int OP_REGISTER = 0x100;
    
    private NioEndpoint endpoint;

    public volatile static boolean test = false;
    
    public Acceptor(NioEndpoint nioEndpoint) {
        endpoint = nioEndpoint;
    }
    
    @Override
    public void run() {
        while (endpoint.isRunning()) {
            try {
                // 申请一个连接名额
                endpoint.countUpOrAwaitConnection();
                
                SocketChannel socket = endpoint.accept();

                try {
                    socket.configureBlocking(false);
                    socket.socket().setTcpNoDelay(true);
                    socket.socket().setSoTimeout(endpoint.getSoTimeout());
                    
                    if (test) { // 用于 EchoProcessor 测试
                        socket.write(EchoProcessor.usage());
                    }

                    NioChannel channel = new NioChannel(socket);
                    endpoint.getPoller().register(channel, OP_REGISTER);
                    log.info("Accept and Register: {}", socket.getRemoteAddress());
                } catch (Throwable t) {
                    try {
                        endpoint.countDownConnection();
                        socket.socket().close();
                        socket.close();
                    } catch (IOException iox) {
                    }
                }

            } catch (SocketTimeoutException ste) {
                ste.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}

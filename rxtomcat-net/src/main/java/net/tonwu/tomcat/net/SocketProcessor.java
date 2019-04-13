package net.tonwu.tomcat.net;

import java.nio.channels.SelectionKey;

import net.tonwu.tomcat.net.Handler.SocketState;

public class SocketProcessor implements Runnable {

    private NioChannel socket;
    
    public SocketProcessor(NioChannel socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // TODO SSL
            
            SelectionKey key = socket.ioChannel().keyFor(socket.getPoller().getSelector());
            
            SocketState state = socket.getPoller().getEndpoint().getHandler().process(socket);
            
            if (state == SocketState.CLOSED) {
                socket.getPoller().cancelledKey(key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

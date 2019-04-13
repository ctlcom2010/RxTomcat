package net.tonwu.tomcat.net;

import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Handler {
    private final Map<NioChannel, Processor> connections = new ConcurrentHashMap<>();
    private String processorClassName;
    
    public enum SocketState {
        /** 长连接 */
        OPEN,
        /** 继续读取 */
        LONG,
        /** 发送 */
        WRITE,
        /** 断开连接 */
        CLOSED
    }
    
    public SocketState process(NioChannel socket) {
        // 是否存在关联的 processor
        Processor processor = connections.get(socket);
        if (processor == null) {
            processor = createProcessor();
        }
        connections.put(socket, processor);
        SocketState state = SocketState.CLOSED;
        state = processor.process(socket);
        
        if (state == SocketState.LONG) {
            socket.getPoller().register(socket, SelectionKey.OP_READ);
        } else if (state == SocketState.OPEN) {
            connections.remove(socket);
            socket.getPoller().register(socket, SelectionKey.OP_READ);
        } else if (state == SocketState.WRITE) {
            socket.getPoller().register(socket, SelectionKey.OP_WRITE);
        } else { // Connection closed
            connections.remove(socket);
        }
        return state;
    }
    
    public void release(NioChannel socket) {
        connections.remove(socket);
    }

    private Processor createProcessor() {
        if (processorClassName != null) {
            try {
                Class<?> clazz = Class.forName(processorClassName);
                return (Processor) clazz.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    
    public void setProcessor(String processorClassName) {
        this.processorClassName = processorClassName;
    }
}

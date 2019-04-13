package net.tonwu.tomcat.net;

import net.tonwu.tomcat.net.Handler.SocketState;

public interface Processor {

    SocketState process(NioChannel socket);

}

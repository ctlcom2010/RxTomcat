package net.tonwu.tomcat.http;

import java.io.IOException;

public interface Adapter {

    void service(RawRequest request, RawResponse response) throws IOException;

}

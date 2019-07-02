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
package net.tonwu.tomcat.container.core;

import java.io.IOException;

import net.tonwu.tomcat.http.Adapter;
import net.tonwu.tomcat.http.HttpProcessor;
import net.tonwu.tomcat.net.Handler;
import net.tonwu.tomcat.net.NioEndpoint;
import net.tonwu.tomcat.net.Processor;

/**
 * 连接器，它主要提供 xml 配置功能，比如指定端口，超时时间等
 * 
 * @author tonwu.net
 */
public class Connector {

	private Context context;
    
    private int port = 10393;
	
	// Endpoint
	private NioEndpoint endpoint; 
	// Adapter
	private Adapter adapter;
	
	public Connector() {
	    adapter = new AdapterImpl(this);
	    endpoint = new NioEndpoint();
	    endpoint.setHandler(new Handler() {
            @Override
            public Processor createProcessor() {
                HttpProcessor processor = new HttpProcessor();
                processor.setAdaptor(adapter);
                return processor;
            }
        });
    }
	
	public void start() throws IOException{
        endpoint.init();
        endpoint.start();
	}

	public void stop() {
	    endpoint.stop();
	}

	// Getter & Setter
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
		endpoint.setPort(port);
	}
	
	public void setContext(Context cxt) {
	    context = cxt;
	}
	
    public Context getContainer() {
        return context;
    }
}

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
package net.tonwu.tomcat.container;

import java.io.IOException;

import javax.servlet.ServletException;

import net.tonwu.tomcat.container.servletx.Request;
import net.tonwu.tomcat.container.servletx.Response;

/**
 * 容器处理流水线，本质就是一个有固定尾节点的链表
 * 
 * @author tonwu.net
 */
public class Pipeline {
    protected Container container = null;
    
    private Valve basic = null;
    private Valve first = null;
    
    public Pipeline(Container container) {
        this.container = container;
    }
    
    /** 从第一个 Valve 开始依次调用它们的 invoke 方法 */
    public void handle(Request request, Response response) throws IOException, ServletException {
        if (first != null) {
            first.invoke(request, response);
        } else {
            basic.invoke(request, response);
        }
    }
    /**
     * 设置固定尾节点
     * 
     * @param newbasic 链表末尾的阀门 Valve
     */
    public void setBasic(Valve newbasic) {
        newbasic.setContainer(container);
        Valve oldBasic = basic;
        if (oldBasic == newbasic)
            return;

        Valve current = first;
        while (current != null) {
            if (current.getNext() == oldBasic) {
                current.setNext(newbasic);
                break;
            }
            current = current.getNext();
        }
        this.basic = newbasic;
    }

    /**
     * 把阀门 Valve 正序插入处理流水线中
     * 
     * @param valve 待插入阀门
     */
    public void addValve(Valve valve) {
        valve.setContainer(container);
        if (first == null) {
            first = valve;
            first.setNext(basic);
        } else {
            Valve current = first;
            while (current != null) {
                if (current.getNext() == basic) {
                    current.setNext(valve);
                    valve.setNext(basic);
                    break;
                }
                current = current.getNext();
            }
        }
    }
}

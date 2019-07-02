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
package net.tonwu.tomcat.http;

/**
 * 容器对 Processor 请求操作的回调机制
 * 
 * @author tonwu.net
 */
public interface ActionHook {
    /**
     * 容器对 Processor 的回调动作
     * @author tonwu.net
     */
    public enum ActionCode {
        ACK,
        /** 请求提交响应头数据到缓冲区 */
        COMMIT,
        /** 请求读取并解析请求参数 */
        PARSE_PARAMS, 
        /** 请求写入响应体数据 */
        WRITE_BODY,
        /** 请求读取请求体数据 */
        READ_BODY,
        /** 请求将响应发送到客户端 */
        FLUSH,
        /** 响应处理完毕 */
        CLOSE
    }

    /**
     * 请求 Processor 处理一个动作
     * 
     * @param actionCode 动作类型
     * @param param 动作发生时关联的参数
     */
    public void action(ActionCode actionCode, Object... param);

}

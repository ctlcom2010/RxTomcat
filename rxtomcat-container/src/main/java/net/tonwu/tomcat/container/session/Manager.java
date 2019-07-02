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
package net.tonwu.tomcat.container.session;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session 对象管理器，负责生成 SessionId、创建 Session、缓存 Session、周期性检查 Session 是否过期
 * 
 * @author tonwu.net
 */
public class Manager {

    protected Map<String, Session> sessions = new ConcurrentHashMap<>();
    protected Random random = new Random();
    
    /**
     * 创建一个 Session 对象
     * 
     * @return Session
     */
    public Session createSession() {
        Session session = new Session();
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(60); // 默认 60s
        String sessionId = generateSessionId();
        session.setId(sessionId);
        session.setManager(this);
        // Timing 控制session超时
        add(session);
        return session;
    }
    
    /**
     * 生成一个唯一的 SessionID 字符串
     * @return SessionID
     */
    protected synchronized String generateSessionId() {
        byte[] randomBytes = new byte[16];

        StringBuilder buffer = new StringBuilder();
        do {
            try {
                random.nextBytes(randomBytes);
                // 将字节数组转为 16 进制字符串
                for (int i = 0; i < randomBytes.length; i++) {
                    int heigh = (randomBytes[i] & 0xf0) >> 4; // 高 4 位
                    int low = (randomBytes[i] & 0xf); // 低 4 位
                    buffer.append(Integer.toHexString(heigh));
                    buffer.append(Integer.toHexString(low));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } while (sessions.containsKey(buffer.toString()));

        return buffer.toString();
    }
    
    /**
     * 周期性操作调用的方法
     */
    public void backgroundProcess() {
        Session[] sessions = findSessions();
        for (Session session : sessions) {
            if (session != null && !session.isValid()) {
                // 失效
            }
        }
    }

    public void add(Session session) {
        sessions.put(session.getId(), session);
    }

    public void remove(String id) {
        sessions.remove(id);
    }

    public Session[] findSessions() {
        return sessions.values().toArray(new Session[0]);
    }

    public Session findSession(String id) {
        return sessions.get(id);
    }

    public void destroy() {
        
    }
}

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
 * HTTP 协议相关标识符
 * 
 * @author tonwu.net
 */
public final class HttpToken {
	 // http 标识符
    public static final byte CR         = (byte) '\r';
    public static final byte LF         = (byte) '\n';
    public static final byte SP         = (byte) ' ';
    public static final byte COLON      = (byte) ':';
    public static final byte SEMI_COLON = (byte) ';';
    public static final byte QUESTION   = (byte) '?';
    
    
    public static final byte[] HTTP_1_1 = "HTTP/1.1 ".getBytes();
    public static final byte[] CRLF = "\r\n".getBytes();
    public static final byte[] END_CHUNK = "0\r\n\r\n".getBytes();
    
    public static final int SC_CONTINUE = 100;
    public static final int SC_OK = 200;
    public static final int SC_BAD_REQUEST = 400;
    public static final int SC_NOT_FOUND = 404;
    public static final int SC_INTERNAL_SERVER_ERROR = 500;
    public static final int SC_NOT_MODIFIED = 304;
    
    static String msg (int code) {
        switch (code) {
          case SC_OK: return " OK";
          case SC_CONTINUE: return " Continue";
          case SC_BAD_REQUEST: return " Bad Request";
          case SC_NOT_FOUND: return " Not Found";
          case SC_INTERNAL_SERVER_ERROR: return " Internal Server Error";
          case SC_NOT_MODIFIED: return " Not Modified";
          default: return "";
        }
      }
}

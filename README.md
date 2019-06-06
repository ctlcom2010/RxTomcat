## RxTomcat

**正在重构和添加注释的过程中...**
为了理解 Tomcat 内部原理，在阅读源码的过程中，尽可能的使用简洁的代码，来模拟实现核心模块的处理流程。

## rxtomcat-net

 - 实现 NIO 模型的核心处理流程
 - 编写了一个简单的回显服务进行测试

**测试**

运行 TestNioEndpoint 的 main 方法，使用 telnet 命令进行测试，在 windows 下记得打开回显（ctrl+] -> set localecho -> Enter）

```bash
==========================================
  Tomcat NIO Test - EchoProcessor
  Author: wskwbog
  Git: http://github.com/tonwu/rxtomcat
  Support: Ctrl+c Backspace
==========================================
NIO> Hello World!
Hello World!
NIO>
```

## rxtomcat-utils

 - 简单实现 Digester 解析 xml 的功能

## rxtomcat-http

 - 实现请求头和请求参数的解析
 - 实现请求体的模拟阻塞读取
 - 实现响应体的 chunked 传输编码以及模拟阻塞发送
 
**测试**

运行 TestHttpProcessor 的 main 方法，发送 http 请求测试

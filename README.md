## RxTomcat

如果你想看 Tomcat 源码但又无从入手，不妨从这个项目开始，代码量不多，但包含了 Tomcat 的核心处理流程，并且源码中有相当丰富的注释。Tomcat 源码分析的文章，可从微信公众号「顿悟源码」获取。

此项目仅用于学习交流使用。

## 如何构建

项目没有过多的依赖，只有 junit 和 servlet-api，以及 logback，构建时您需要：

 - Oracle JDK 8
 - Apache Maven

注意，这只是构建是需要，JDK 7 或 8 足以运行。

使用以下命令，构建：

```
  mvn clean install
```

最终打包版本会生成在 `rxtomcat-bootstrap/target/dist/` 目录中，打包时会执行 test 和 生成 javadoc，如要跳过可使用以下命令：

```
  mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

最终构建生成的项目运行目录结构是：

```
  rxtomcat-x.x.x
  ├── bin
  │   ├── bootstrap.jar
  │   ├── logback.xml
  │   ├── rxtomcatd.bat
  │   └── rxtomcatd.sh
  ├── lib
  │   ├── logback-classic-1.2.3.jar
  │   ├── logback-core-1.2.3.jar
  │   ├── rxtomcat-container-2.0.0-SNAPSHOT.jar
  │   ├── rxtomcat-http-2.0.0-SNAPSHOT.jar
  │   ├── rxtomcat-net-2.0.0-SNAPSHOT.jar
  │   ├── rxtomcat-utils-2.0.0-SNAPSHOT.jar
  │   ├── servlet-api-2.5.jar
  │   └── slf4j-api-1.7.25.jar
  ├── LICENSE
  ├── logs
  ├── README.txt
  ├── VERSION.txt
  ├── webapp
  │   └── test
  └── work
```

## 核心模块介绍

RxTomcat 主要对 NIO 模型、HTTP 协议解析、Digester 工具、Servlet 容器以及集群这些核心模块进行实现。

### rxtomcat-net

NIO 模型的实现，从通道读取字节到处理请求都是一个线程，只有在非阻塞读取不完整的请求头数据时，才有可能切换线程。原生 NIO 编程比较复杂，主要是处理好通道和处理器的映射，以及各种状态的管理。

### rxtomcat-http

完整的实现 HTTP 协议是很复杂的，这里只是简单的实现了一小部分，主要实现了以下功能：

 - 消息行（请求或响应）的解析和构造，解析时采用**有限状态机**的方法
 - chunked 和 identity 消息体的解析和构造
 - 实现 keepAlive 长连接
 - 特殊 URL 的处理，比如 param=%E5%88%9B+a
 - 为容器提供底层 Processor 的 ActionHook 回调机制

解析协议麻烦的地方在于处理 **TCP 粘包拆包**的问题，以及各种缓冲区的清空和重用。

### rxtomcat-container

Servlet 容器的简单实现，主要实现的功能有：

 - Pipeline 和 Valve 的管道处理模型，以及容器 Lifecycle 生命周期的设计
 - DefaultServlet 静态资源的处理和缓存
 - 根据 web.xml 部署应用，提取 Servlet 和 Filter 及其配置的映射
 - 打破双亲委托的类加载器 Loader，实现从 WEB-INF/classes 和 WEB-INF/lib 加载类，以及 class 文件**热加载**的功能
 - 实现 Servlet 的三种 URL 路由规则，以及规范中的 Cookie, HttpSession, FilterChain, HttpServletRequest, HttpServletResponse
 - 实现 Session 以及它的管理器 Manager

### rxtomcat-ha

模拟实现集群。TODO

### rxtomcat-utils

功能辅助类，主要实现了：

 - 简单实现 Digester 解析 xml 的功能
 - Bytes 操作字节数组的工具类

### rxtomcat-bootstrap

用于构建的模块，它包含一个含有 main 方法的 Bootstrap 类，这个类使用反射创建容器并启动。注意，使用反射原因是因为在发布的二进制目录中，它不能识别 Context 等任何其他模块的类，只能使用反射调用它的方法。


RxTomcat
========
RxTomcat 是对 Tomcat 核心流程的模拟实现，基本实现了： NIO 服务器模型，HTTP 协议
解析，基本 Sevlet 容器，以及类加载器，管道处理模型等

  https://github.com/tonwu/RxTomcat/

此项目仅用于学习交流使用


运行 Rxtomcat
=============
运行时可使用发布的二进制版本或者在 rxtomcat-bootstrap/target/ 目录下使用源码构建的版本。

可执行脚本位于安装目录的 bin 目录，按平台的不同分别执行 rxtomcatd.sh 或 rxtomcatd.bat：

  $ ./rxtomcatd.sh start
  C:\rxtomcat-x.x.x\bin\rxtomcatd.exe


  
  关于 Tomcat 源码分析的文章，以及更多「造的轮子」源码，欢迎关注微信公众号「顿悟源码」获取。

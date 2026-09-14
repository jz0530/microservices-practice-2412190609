# 第一周作业：开发环境与个人仓库

## 环境检查

本次使用 Windows + Ubuntu 26.04 LTS（WSL 2），通过 VS Code 连接 Ubuntu 开发，Docker Desktop 已启用 WSL 集成。

以下为在 Ubuntu 中执行的环境检查命令及输出。

### Java

```text
$ java --version
openjdk 26.0.2 2026-07-21
OpenJDK Runtime Environment (build 26.0.2+10-2-26.04.2-Ubuntu)
OpenJDK 64-Bit Server VM (build 26.0.2+10-2-26.04.2-Ubuntu, mixed mode, sharing)
```

### Maven

```text
$ mvn --version
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Maven home: /opt/maven
Java version: 26.0.2, vendor: Ubuntu, runtime: /usr/lib/jvm/java-26-openjdk-amd64
Default locale: en, platform encoding: UTF-8
OS name: "linux", version: "6.18.33.2-microsoft-standard-wsl2", arch: "amd64", family: "unix"
```

### Git

```text
$ git --version
git version 2.53.0
```

### Docker

```text
$ docker version
Client:
 Version:           29.7.2
 API version:       1.55
 Go version:        go1.26.5
 Git commit:        a7dcaa6
 Built:             Wed Aug  5 18:27:38 2026
 OS/Arch:           linux/amd64
 Context:           default

Server: Docker Desktop 4.90.0 (238679)
 Engine:
  Version:          29.7.2
  API version:      1.55 (minimum version 1.40)
  Go version:       go1.26.5
  Git commit:       6a43e3d
  Built:            Wed Aug  5 18:28:36 2026
  OS/Arch:          linux/amd64
  Experimental:     false
 containerd:
  Version:          v2.3.3
  GitCommit:        aad11006b869517fcd3009450b6f82da282e1a9b
 runc:
  Version:          1.4.3
  GitCommit:        v1.4.3-0-gbb14dabe
 docker-init:
  Version:          0.19.0
  GitCommit:        de40ad0
```

### Docker Compose

```text
$ docker compose version
Docker Compose version v5.5.1
```



## 概念回答

### 1. 什么是微服务架构？

根据课上内容以及例子 我认为微服务架构是将一个庞大的系统拆分成几个小的服务 这几个服务可以在自己的进程中运行，不同服务可以放在不同的机器上，并且可以独立开发、部署和扩容。

### 2. 微服务和单体架构的主要区别是什么？
传统单体架构特点：用户界面->业务逻辑层->数据访问层->数据库
紧耦合（无法剥离）
单体扩容很困难
但是微服务架构就可以实现某一服务的扩容（例如淘宝双十一 只需要在支付服务上扩容而不是一整个全部）
### 3. 为什么本课程先实现单体系统，再逐步拆分为微服务？
有可能是因为我们要先理解单体结构的运行逻辑，帮助我们理解完整的业务流程，先把基本功能做出来，且单体结构适合体量小的项目，从小做到大。而且微服务的运维复杂，服务通信，测试复杂性都比单体系统高，从简单到容易

### 4. 为什么作业需要提供可重复运行的测试或验证脚本？

因为一次运行成功，不能保证换一台电脑或者修改代码后仍然正常。测试或验证脚本可以把检查步骤固定下来，方便自己和老师重复验证结果。以后修改功能时，也能重新运行这些脚本，检查原来的功能是否受到影响，并帮助定位问题。


## 问题记录

### Docker 访问权限不足

现象：执行 docker version 时只能看到 Client 信息，并提示 permission denied。

排查：使用 id 和 ls -l /var/run/docker.sock 检查，发现当前用户不属于 docker 组，而接口文件允许 docker 组访问。

处理：使用 sudo usermod -aG docker chengyouyang 将用户加入 docker 组。由于 newgrp 命令未安装，通过退出并重新进入 Ubuntu 刷新组权限。

结果：docker version 能正常显示 Client 和 Server，hello-world 容器运行成功。

### Ubuntu 中找不到 code 命令

现象：执行 code . 时提示 Command 'code' not found。

处理：从 Windows 的 VS Code 命令面板选择 WSL: Connect to WSL using Distro...，连接 Ubuntu-26.04，再打开课程目录。


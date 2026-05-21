# Maven 完整知识总结

本文用于阶段 1 开发前复习 Maven。重点不是背概念，而是能看懂 `pom.xml`，知道依赖从哪里来、版本由谁管、模块之间怎么组织。

## 1. Maven 是干什么的

Maven 主要解决三件事：

1. 管理项目结构。
2. 管理第三方依赖。
3. 统一编译、测试、打包、运行流程。

不用 Maven 时，你可能要手动下载 jar、配置 classpath、自己处理版本冲突。Maven 把这些事情统一写到 `pom.xml` 里。

## 2. POM 是什么

POM 全称是：

```text
Project Object Model
```

可以理解为：

```text
项目说明书
```

一个 Maven 项目通常有一个 `pom.xml`，里面描述：

- 当前项目叫什么。
- 当前项目版本是多少。
- 当前项目打包成什么。
- 当前项目依赖哪些库。
- 当前项目有哪些子模块。
- 当前项目使用哪些插件。

最基本结构：

```xml
<project>
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.ls</groupId>
    <artifactId>agent-platform</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
</project>
```

## 3. Maven 坐标

一个依赖靠三个核心坐标定位：

```text
groupId + artifactId + version
```

例如：

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
</dependency>
```

含义：

| 字段 | 含义 |
| --- | --- |
| `groupId` | 组织或包名 |
| `artifactId` | 具体项目或 jar 名 |
| `version` | 版本号 |

在多模块项目中，自己的模块也有 Maven 坐标。例如：

```xml
<groupId>com.ls</groupId>
<artifactId>agent-platform-core</artifactId>
<version>1.0.0-SNAPSHOT</version>
```

## 4. `packaging` 是什么

`packaging` 表示当前 Maven 项目最终打包类型。

常见类型：

| 类型 | 含义 |
| --- | --- |
| `pom` | 父工程或聚合工程，不产出普通 jar |
| `jar` | 普通 Java jar 包 |
| `war` | 传统 Web 应用包，现在 Spring Boot 项目较少用 |

你的项目根模块应该是：

```xml
<packaging>pom</packaging>
```

因为根模块主要负责管理子模块，不直接写业务代码。

子模块一般是：

```xml
<packaging>jar</packaging>
```

## 5. 父 POM 和子模块

多模块项目通常这样：

```text
agent-platform
├─ pom.xml
├─ agent-platform-common
├─ agent-platform-core
├─ agent-platform-web
└─ agent-platform-gateway
```

根目录 `pom.xml` 是父 POM。

父 POM 里写：

```xml
<modules>
    <module>agent-platform-common</module>
    <module>agent-platform-core</module>
    <module>agent-platform-web</module>
    <module>agent-platform-gateway</module>
</modules>
```

这表示：

```text
这四个目录是当前父工程管理的子模块。
```

注意：

```text
modules 负责聚合构建，不等于继承父 POM。
```

子模块要继承父 POM，需要在子模块 `pom.xml` 里写：

```xml
<parent>
    <groupId>com.ls</groupId>
    <artifactId>agent-platform</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

这表示：

```text
当前子模块继承父 POM 的依赖版本管理、插件配置、公共属性等。
```

所以要区分：

| 写法 | 作用 |
| --- | --- |
| 父 POM 的 `<modules>` | 父工程构建时把哪些子模块一起构建 |
| 子 POM 的 `<parent>` | 子模块继承哪个父 POM 的配置 |

## 6. `dependencies`

`dependencies` 表示当前模块真正引入的依赖。

例如 `web` 模块需要 Spring MVC：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

这表示：

```text
web 模块真正需要 spring-boot-starter-web。
```

## 7. 父 POM 里的 `dependencies` 会被子模块继承

这是很容易漏掉的一点。

如果父 POM 写：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

那么所有继承它的子模块都会拿到这个依赖。

结果可能变成：

```text
common 有 web 依赖
core 有 web 依赖
gateway 有 web 依赖
web 有 web 依赖
```

这通常不是你想要的。

所以多模块项目里：

```text
父 POM dependencies 要谨慎使用。
业务依赖通常放到具体子模块的 dependencies 里。
```

父 POM 的 `dependencies` 适合放所有子模块都一定要用的依赖，例如少量测试基础依赖。但即使是测试依赖，也要谨慎。

## 8. `dependencyManagement`

`dependencyManagement` 只管理版本，不真正引入依赖。

父 POM 可以写：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>42.7.3</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

这只是规定：

```text
如果子模块以后使用 postgresql，就默认用 42.7.3。
```

子模块还要自己声明：

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

这样子模块才真正引入 PostgreSQL。

## 9. `dependencyManagement` 和 `dependencies` 对比

| 位置 | 作用 | 是否真正引入 jar | 子模块是否自动拥有 |
| --- | --- | --- | --- |
| 父 POM `dependencyManagement` | 管版本 | 否 | 否 |
| 父 POM `dependencies` | 引依赖 | 是 | 是 |
| 子 POM `dependencies` | 引当前模块依赖 | 是 | 当前模块有 |

记忆：

```text
dependencyManagement 是菜单价格表。
dependencies 是真正点菜。
父 dependencies 是给所有子模块统一上菜。
```

## 10. BOM

BOM 全称：

```text
Bill of Materials
```

在 Maven 里可以理解为：

```text
一份依赖版本清单。
```

Spring Boot BOM 是：

```text
Spring Boot 官方维护的一整套依赖版本管理。
```

导入方式：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.4.13</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

导入后，子模块可以不写很多 Spring 生态依赖的版本。

## 11. `<type>pom</type>` 和 `<scope>import</scope>`

这两个通常只用于导入 BOM。

```xml
<type>pom</type>
<scope>import</scope>
```

含义：

| 标签 | 含义 |
| --- | --- |
| `<type>pom</type>` | 说明这个依赖是一个 POM 文件，不是 jar |
| `<scope>import</scope>` | 把这个 POM 里的 dependencyManagement 导入进来 |

普通 jar 依赖不要写这两个。

## 12. 依赖 scope

`scope` 表示依赖在哪些阶段可用。

常见 scope：

| scope | 含义 |
| --- | --- |
| `compile` | 默认值，编译和运行都需要 |
| `test` | 只在测试代码里使用 |
| `runtime` | 编译不需要，运行需要 |
| `provided` | 编译需要，运行环境提供 |
| `import` | 只用于 dependencyManagement 导入 BOM |

例子：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

表示：

```text
这个依赖只给测试代码使用。
```

## 13. 依赖传递

Maven 依赖有传递性。

如果：

```text
web 依赖 core
core 依赖 common
```

那么：

```text
web 通常也能间接拿到 common。
```

例子：

```xml
<!-- web/pom.xml -->
<dependency>
    <groupId>com.ls</groupId>
    <artifactId>agent-platform-core</artifactId>
</dependency>
```

如果 `core` 依赖 `common`，`web` 会通过 `core` 间接拿到 `common`。

但不要依赖这种传递关系来表达架构边界。重要模块依赖建议显式写清楚。

## 14. 排除传递依赖

有时一个依赖会带进来你不想要的库，可以排除：

```xml
<dependency>
    <groupId>some.group</groupId>
    <artifactId>some-lib</artifactId>
    <exclusions>
        <exclusion>
            <groupId>bad.group</groupId>
            <artifactId>bad-lib</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

阶段 1 初期不一定会用到，但看见 `exclusions` 要知道它是在排除传递依赖。

## 15. properties

父 POM 可以用 `properties` 统一定义版本号或编码。

```xml
<properties>
    <java.version>21</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <mybatis.version>3.0.3</mybatis.version>
</properties>
```

然后在依赖里引用：

```xml
<version>${mybatis.version}</version>
```

这样后面升级版本只改一个地方。

## 16. build 和 plugins

`build` 里通常配置 Maven 插件。

例如 Spring Boot 打包插件：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

插件负责执行构建动作，例如：

- 编译。
- 测试。
- 打包。
- 生成可运行 jar。
- 执行代码检查。

## 17. `pluginManagement`

类似 `dependencyManagement`。

`pluginManagement` 只管理插件版本和默认配置，不一定执行插件。

父 POM 可以写：

```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>21</release>
                </configuration>
            </plugin>
        </plugins>
    </pluginManagement>
</build>
```

子模块或父模块真正使用插件时，再放到 `plugins` 里。

## 18. Maven 生命周期

常用命令：

| 命令 | 含义 |
| --- | --- |
| `mvn clean` | 清理 `target` 目录 |
| `mvn compile` | 编译主代码 |
| `mvn test` | 运行测试 |
| `mvn package` | 编译、测试、打包 |
| `mvn install` | 打包并安装到本地 Maven 仓库 |

常用顺序：

```powershell
mvn clean test
mvn clean package
```

## 19. Maven 仓库

Maven 找依赖通常按这个顺序：

```text
本地仓库 -> 远程仓库
```

本地仓库默认在：

```text
C:\Users\你的用户名\.m2\repository
```

第一次使用某个依赖时，Maven 会从远程仓库下载到本地仓库。以后再用就不需要重复下载。

## 20. SNAPSHOT 版本

例如：

```text
1.0.0-SNAPSHOT
```

表示开发中的快照版本，不是正式发布版本。

阶段 1 项目可以使用：

```xml
<version>1.0.0-SNAPSHOT</version>
```

等项目稳定后再改成正式版本。

## 21. 阶段 1 项目的推荐 Maven 结构

```text
agent-platform
├─ pom.xml                         父 POM，packaging=pom
├─ agent-platform-common
│  └─ pom.xml
├─ agent-platform-core
│  └─ pom.xml
├─ agent-platform-web
│  └─ pom.xml
└─ agent-platform-gateway
   └─ pom.xml
```

父 POM 负责：

- 声明四个子模块。
- 统一 Java 版本。
- 导入 Spring Boot BOM。
- 导入 Spring AI BOM。
- 管理 MyBatis、ArchUnit 等额外依赖版本。
- 管理 Maven 插件版本。

子模块 POM 负责：

- 继承父 POM。
- 声明自己真正需要的依赖。
- 按模块边界依赖其他模块。

## 22. 阶段 1 模块依赖原则

应该是：

```text
core -> common
web -> core + common
gateway -> core + common
web -> gateway 只走 HTTP，不写 Maven 依赖
```

也就是说：

```text
web 模块不能在 pom.xml 里依赖 agent-platform-gateway。
```

因为设计上 `web -> gateway` 是 HTTP 转发，不是代码调用。

## 23. 常见错误

### 错误 1：以为 `dependencyManagement` 会自动引入依赖

不会。

它只管版本。

### 错误 2：把业务依赖放进父 POM `dependencies`

这样所有子模块都会继承，容易污染模块边界。

例如不要让 `common` 也拿到 `spring-boot-starter-web`。

### 错误 3：普通依赖写 `scope=import`

`import` 基本只用于 BOM。

普通 jar 依赖不要写：

```xml
<scope>import</scope>
```

### 错误 4：子模块忘记写 `<parent>`

如果子模块没有继承父 POM，就拿不到父 POM 的版本管理和插件配置。

### 错误 5：只写 `<modules>` 以为就继承了

`<modules>` 只是聚合构建。

继承关系要靠子模块里的 `<parent>`。

## 24. 最终记忆图

```text
父 POM
│
├─ modules
│   └─ 聚合子模块，一起构建
│
├─ dependencyManagement
│   ├─ 导入 Spring Boot BOM
│   ├─ 导入 Spring AI BOM
│   └─ 手动管理额外依赖版本
│
├─ dependencies
│   └─ 会被子模块继承，谨慎放
│
└─ pluginManagement
    └─ 管理插件版本和默认配置

子模块 POM
│
├─ parent
│   └─ 继承父 POM
│
└─ dependencies
    └─ 当前模块真正使用的依赖
```

## 25. 记忆口诀

```text
parent 才是继承。
modules 只是聚合。
dependencyManagement 管版本。
dependencies 真引包。
父 dependencies 会传给子模块。
BOM 是别人维护好的版本表。
普通 jar 不 import，只有 BOM 才 import。
```

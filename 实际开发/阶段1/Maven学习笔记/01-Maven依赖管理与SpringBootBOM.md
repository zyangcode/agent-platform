# Maven 依赖管理与 Spring Boot BOM

本文用于记录阶段 1 开发前必须理解的 Maven 基础，重点解释父工程、子模块、`dependencyManagement`、`dependencies`、BOM、Spring Boot BOM 之间的关系。

## 1. 先记住一句话

```text
dependencyManagement 负责管版本。
dependencies 负责真正引入依赖。
BOM 是别人维护好的一整套 dependencyManagement 版本清单。
```

也就是说：

- 父模块在 `dependencyManagement` 里声明版本，只是告诉子模块“以后如果你用这个依赖，就用这个版本”。
- 子模块必须在自己的 `dependencies` 里声明依赖，依赖才会真的进入当前模块。
- 子模块如果用的依赖已经被父模块或 BOM 管理了版本，就可以不写 `<version>`。

## 2. 四个核心概念

| 概念 | 作用 | 会不会真正引入 jar |
| --- | --- | --- |
| 父 POM | 管理多模块、统一依赖版本、统一插件配置 | 不一定 |
| `dependencyManagement` | 统一声明依赖版本 | 不会 |
| `dependencies` | 当前模块实际使用哪些依赖 | 会 |
| BOM | 一个专门用于批量管理版本的 POM 文件 | 不会 |

## 3. 父模块自己管理依赖版本

父模块可以这样写：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>42.7.3</version>
        </dependency>

        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>3.0.3</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

这表示父模块统一规定：

```text
如果子模块使用 PostgreSQL 驱动，就默认用 42.7.3。
如果子模块使用 MyBatis Spring Boot Starter，就默认用 3.0.3。
```

但是上面这段不会自动把 PostgreSQL 和 MyBatis 加进所有子模块。

子模块还要自己写：

```xml
<dependencies>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
</dependencies>
```

因为父模块已经管了版本，所以子模块这里不用写：

```xml
<version>42.7.3</version>
```

## 4. Spring Boot BOM 是什么

BOM 全称是：

```text
Bill of Materials
```

可以理解为：

```text
依赖版本清单
```

Spring Boot BOM 指的是：

```text
Spring Boot 官方维护的一大张依赖版本表。
```

常见写法：

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

这段的意思是：

```text
导入 Spring Boot 3.4.13 官方推荐的依赖版本组合。
```

导入之后，很多常见依赖都可以不写版本号。

例如：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

这些依赖的版本由 Spring Boot BOM 统一决定。

## 5. `<type>pom</type>` 和 `<scope>import</scope>`

这两个标签通常只在导入 BOM 时一起使用。

```xml
<type>pom</type>
<scope>import</scope>
```

含义分别是：

| 标签 | 含义 |
| --- | --- |
| `<type>pom</type>` | 说明导入的是一个 POM 文件，不是普通 jar |
| `<scope>import</scope>` | 说明把这个 POM 里面的版本管理规则导入当前项目 |

普通依赖不要这样写。

正确的普通依赖：

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

错误示例：

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

因为 PostgreSQL 驱动是 jar 依赖，不是 BOM。

## 6. Spring Boot BOM 不是多个父模块组成的

更准确地说：

```text
Spring Boot BOM 本身就是一个特殊的 pom.xml。
这个 pom.xml 里面主要放了一个很大的 dependencyManagement。
```

它大概长这样：

```xml
<project>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-dependencies</artifactId>
    <version>3.4.13</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework</groupId>
                <artifactId>spring-core</artifactId>
                <version>6.1.x</version>
            </dependency>

            <dependency>
                <groupId>com.fasterxml.jackson.core</groupId>
                <artifactId>jackson-databind</artifactId>
                <version>2.17.x</version>
            </dependency>

            <dependency>
                <groupId>org.postgresql</groupId>
                <artifactId>postgresql</artifactId>
                <version>42.x.x</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

所以：

```text
Spring Boot BOM = Spring Boot 官方维护的 dependencyManagement 大清单。
```

## 7. 一个父工程可以同时导入多个 BOM

你的项目父 POM 里可以同时导入 Spring Boot BOM、Spring AI BOM，也可以自己额外指定依赖版本。

```xml
<dependencyManagement>
    <dependencies>
        <!-- Spring Boot 官方 BOM -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.4.13</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- Spring AI 官方 BOM -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- 项目自己额外管理的依赖版本 -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>3.0.3</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

可以理解为：

```text
你的父 POM dependencyManagement
    = 导入 Spring Boot BOM
    + 导入 Spring AI BOM
    + 自己手动补充部分依赖版本
```

## 8. Spring Boot Parent 和 Spring Boot BOM 的区别

有些 Spring Boot 项目会这样写：

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.13</version>
</parent>
```

这是直接继承 Spring Boot 官方父 POM。

它通常会帮你做两类事：

- 导入 Spring Boot 的依赖版本管理。
- 配置一些 Maven 插件默认规则。

但是你的项目是多模块项目，而且已经需要自己的父工程管理：

```text
agent-platform-parent
    agent-platform-common
    agent-platform-core
    agent-platform-web
    agent-platform-gateway
```

这种情况下，通常不直接继承 `spring-boot-starter-parent`，而是在自己的父 POM 里导入 `spring-boot-dependencies` BOM。

推荐思路：

```text
自己的父 POM 负责项目模块和工程规则。
Spring Boot BOM 负责 Spring 生态依赖版本。
```

## 9. 子模块什么时候不用写版本号

子模块不用写版本号的前提是：

```text
父模块 dependencyManagement 或导入的 BOM 已经管理了这个依赖版本。
```

例如父模块已经导入 Spring Boot BOM：

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

那么 `web` 子模块可以这样写：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

如果父模块没有管理某个依赖版本，子模块直接不写版本会报错。

例如父模块没有管理某个第三方库：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>some-lib</artifactId>
</dependency>
```

Maven 可能会报：

```text
'dependencies.dependency.version' is missing
```

解决方式有两个：

1. 在父模块 `dependencyManagement` 里统一管理它。
2. 在当前子模块依赖里直接写版本。

多模块项目更推荐第一种。

## 10. 图：Maven 依赖管理关系

可编辑图文件在同目录：

```text
实际开发/阶段1/Maven学习笔记/Maven依赖管理关系.drawio
```

文本版结构如下：

```text
agent-platform 父 POM
│
├─ modules
│   ├─ agent-platform-common
│   ├─ agent-platform-core
│   ├─ agent-platform-web
│   └─ agent-platform-gateway
│
├─ dependencyManagement
│   ├─ import Spring Boot BOM
│   │   └─ 管理 starter-web、validation、test、Jackson、Tomcat、PostgreSQL 等版本
│   │
│   ├─ import Spring AI BOM
│   │   └─ 管理 Spring AI 相关依赖版本
│   │
│   └─ 手动管理项目额外依赖版本
│       ├─ MyBatis
│       ├─ ArchUnit
│       └─ 其他 BOM 没管住的库
│
└─ 子模块 dependencies
    ├─ 声明当前模块真正要用哪些依赖
    ├─ 如果版本已被父 POM 管理，则不写 version
    └─ 如果版本没被管理，则必须补 version 或回父 POM 统一管理
```

## 11. 阶段 1 项目里的使用原则

阶段 1 开发时建议这样做：

1. 根目录父 POM 使用 `packaging=pom`。
2. 父 POM 管理四个 Maven 子模块。
3. 父 POM 通过 `dependencyManagement` 导入 Spring Boot BOM。
4. 父 POM 通过 `dependencyManagement` 导入 Spring AI BOM。
5. 父 POM 手动补充 MyBatis、ArchUnit 等未被合适 BOM 管理的版本。
6. 子模块只在 `dependencies` 里声明自己真正需要的依赖。
7. 子模块尽量不写版本号。
8. 如果子模块必须写版本号，优先考虑是不是应该提到父 POM 统一管理。

## 12. 最容易混淆的点

### `dependencyManagement` 不会自动引入依赖

父模块写：

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

不代表所有子模块都有 PostgreSQL。

只有子模块写了：

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

这个子模块才真的有 PostgreSQL 依赖。

### BOM 不等于父模块

父模块是你的项目结构父级。

BOM 是依赖版本清单。

它们都是 POM 文件，但用途不同。

### `import` 只用于 BOM

`<scope>import</scope>` 不是普通依赖的 scope。

普通依赖常见 scope 是：

- `compile`
- `test`
- `runtime`
- `provided`

`import` 基本只放在：

```xml
<dependencyManagement>
```

里面，用来导入 BOM。

## 13. 记忆口诀

```text
父 POM 定规矩。
BOM 给版本表。
dependencyManagement 管版本。
dependencies 真引包。
子模块用依赖，先看父里有没有版本。
普通 jar 不写 import，只有 BOM 才 import。
```

# Maven 依赖管理关系图

如果 `.drawio` 打不开，先看这个可读版。它和图表达的是同一件事。

```mermaid
flowchart TB
    A[父 POM<br/>agent-platform/pom.xml<br/>packaging=pom]

    A --> B[子模块 parent<br/>继承父 POM]
    A --> C[modules<br/>聚合构建<br/>不等于依赖]
    A --> D[dependencyManagement<br/>只管版本<br/>不引入 jar]
    A --> E[父 dependencies<br/>真引包<br/>会传给所有子模块<br/>本项目慎用]

    C --> M1[common]
    C --> M2[core]
    C --> M3[web]
    C --> M4[gateway]

    D --> BOM1[Spring Boot BOM<br/>3.4.13<br/>type=pom scope=import]
    D --> BOM2[Spring AI BOM<br/>1.0.7<br/>type=pom scope=import]
    D --> V[额外版本<br/>MyBatis-Plus / ArchUnit / JJWT]

    BOM1 -.提供 version.-> F[子模块 dependencies<br/>真正引入 jar<br/>不写 version]
    V -.提供 version.-> F

    M2 --> M1
    M3 --> M2
    M4 --> M2

    M3 -.依赖传递.-> T[web -> core -> common<br/>web 间接拿到 common<br/>直接使用 common 类型时仍建议显式声明]
    T --> S[scope 影响传递范围<br/>test / runtime / optional]
```

## 记忆口诀

```text
parent：子模块继承父 POM。
modules：父工程聚合构建子模块，不代表模块互相依赖。
dependencyManagement：只管版本，不引入 jar。
父 dependencies：会真引包，并传给所有子模块，慎用。
子模块 dependencies：当前模块真正要用什么 jar，就在这里声明。
BOM：一张官方版本表，用 type=pom + scope=import 导入 dependencyManagement。
```

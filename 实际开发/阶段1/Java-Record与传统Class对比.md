# Java Record vs 传统 Class：用同一套逻辑看差异

> 以 agent-platform 项目中的 `ApiResponse<T>` 为例，对比传统 class 与 record 的代码量、不可变性、构造函数写法。

---

## 1. 语法简化与定位

**传统 Class**——样板代码淹没业务意图：

```java
public final class ApiResponse<T> {
    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    public ApiResponse(boolean success, String code, String message, T data) {
        if (code == null || message == null) {
            throw new NullPointerException();
        }
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public boolean isSuccess() { return success; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }

    // 还要写 equals / hashCode / toString（省略约 20 行）
}
```

**Record**——一行定义，编译器自动生成一切：

```java
public record ApiResponse<T>(
    boolean success,
    String code,
    String message,
    T data
) {
    // 只写校验，其他全自动
    public ApiResponse {
        Objects.requireNonNull(code);
        Objects.requireNonNull(message);
    }
}
```

```java
// 使用对比
ApiResponse<String> res = ApiResponse.success("Hello");

// Record：字段同名方法
System.out.println(res.code());         // "OK"

// Class：getter
System.out.println(res.getCode());      // "OK"

// Record：自动 toString
System.out.println(res);
// ApiResponse[success=true, code=OK, message=success, data=Hello]
```

---

## 2. 不可变性

Record 的字段**强制 final**，创建后不可修改：

```java
ApiResponse<String> res = ApiResponse.success("Hello");
// res.code = "ERROR";  // 编译报错：cannot assign a value to final variable code
```

传统 class 如果不手动加 `final` + 不写 setter，字段仍可能被内部修改。Record 从编译器层面锁死，天然线程安全，适合 API 响应这种"创建即不变"的场景。

---

## 3. 紧凑构造函数

**传统 Class**：构造函数每行都在赋值：

```java
public ApiResponse(boolean success, String code, String message, T data) {
    if (code == null || message == null) {
        throw new NullPointerException();
    }
    this.success = success;   // ← 样板
    this.code = code;         // ← 样板
    this.message = message;   // ← 样板
    this.data = data;         // ← 样板
}
```

**Record**：赋值由编译器隐式完成，你只写校验：

```java
public record ApiResponse<T>(boolean success, String code, String message, T data) {
    public ApiResponse {
        Objects.requireNonNull(code);
        Objects.requireNonNull(message);
        // 编译器自动在此执行：this.code = code; this.message = message; ...
    }
}
```

甚至可以预处理参数：

```java
public record TrimmedString(String value) {
    public TrimmedString {
        value = value.trim();  // 编译器最后执行 this.value = value;
    }
}
```

---

## 4. 深度对比：PageResult 示例

以分页结果 `PageResult<T>` 为例，进一步看清 Record 隐藏了多少“体力活”。

### 传统 Class 实现（约 100 行）
```java
public final class PageResult<T> {
    private final List<T> records;
    private final long pageNo;
    private final long pageSize;
    private final long total;
    private final long totalPages;

    public PageResult(List<T> records, long pageNo, long pageSize, long total, long totalPages) {
        this.records = List.copyOf(Objects.requireNonNull(records));
        if (pageNo < 1) throw new IllegalArgumentException("...");
        // ... 其他校验
        this.pageNo = pageNo;   // 显式赋值
        this.pageSize = pageSize;
        this.total = total;
        this.totalPages = totalPages;
    }

    public List<T> getRecords() { return records; }
    public long getPageNo() { return pageNo; }
    // ... 其他 getter, equals, hashCode, toString
}
```

### Record 实现（约 15 行）
```java
public record PageResult<T>(
    List<T> records,
    long pageNo,
    long pageSize,
    long total,
    long totalPages
) {
    public PageResult {
        // 1. 只写校验或转换逻辑
        records = List.copyOf(Objects.requireNonNull(records));
        if (pageNo < 1) throw new IllegalArgumentException("...");
        
        // 2. 赋值逻辑（this.x = x）由编译器全自动完成，无需编写
    }
}
```

---

## 5. 对比总结表格

| 任务 | 传统 Class | **Record** |
| :--- | :--- | :--- |
| **定义变量** | 手动声明 `private final ...` | **自动**（写在 Header 括号内） |
| **赋值逻辑** | 手动编写 `this.x = x` | **自动** |
| **参数校验** | 手动编写 `if (...)` | 手动编写 `if (...)` |
| **Getter** | 手动编写 `getXxx()` | **自动生成同名方法**（如 `records()`） |
| **基础方法** | 手动重写 `equals`/`hashCode`/`toString` | **自动生成** |

---

## 6. 什么场景用 Record，什么场景用 Class

| 场景 | 选型 | 原因 |
|------|:--:|------|
| API 响应 / 请求 DTO | Record | 纯数据载体，不可变，线程安全 |
| 数据库 Entity | Class | 需要可变字段 + MyBatis-Plus 字段映射 |
| Service（业务逻辑） | Class | 需要依赖注入、状态、行为 |
| Controller | Class | 需要 `@RestController` 等注解 |
| 配置类 | Record | `@ConfigurationProperties` 支持 Record |
| 事件对象 | Record | 天然不可变，适合异步传递 |

---

## 一句话总结

**Record 是 Java 给"数据类"的原生语法糖**——不写构造函数、不写 Getter、不写 equals/hashCode/toString，编译器全自动搞定。在不用 Lombok 的项目里，它是保持代码整洁的最佳选择。

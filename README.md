<div align="right">

[中文](./README.md) | [English](./README.en.md)

</div>

# 什么是mybatis

>MyBatis是一个持久层框架，支持自定义SQL、存储过程以及高级映射。它减少了JDBC设置参数和获取结果集的代码工作，通过XML或注解将Java的原始类型、接口、POJO映射到数据库记录中。

## 核心组件

1. SqlSessionFactory：创建SqlSession的工厂。

2. SqlSession：提供了与数据库交互的主要接口，管理数据库连接、事务处理。

3. Mapper：Mapper接口定义了数据库操作方法，通过绑定注解或者XML进行映射SQL，执行数据库操作方法时，动态代理对象拦截后调用executor执行对应的SQL。

4. Configruation：MyBatis的全局配置类，包含所有配置信息，包括数据源、事务管理、映射器、插件等。

5. Executor：负责执行具体的SQL操作，并处理结果集的映射。

# 入门

## 安装Mybatis

项目采用Maven构建，只需要在`pom.xml`中导入MyBatis、Mysql连接依赖，项目以Mysql数据库为例进行实践。

```xml
<!-- MyBatis -->
<dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis</artifactId>
    <version>3.5.16</version>
</dependency>
<!-- MySQL Connector -->
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.17</version>
</dependency>
```

## 构建SqlSessionFactory

MyBatis 的核心是 `SqlSessionFactory`，它负责创建和管理 `SqlSession` 对象。要获得 `SqlSessionFactory` 实例，可以使用 `SqlSessionFactoryBuilder`。`SqlSessionFactoryBuilder` 能够通过读取 XML 配置文件（如 `mybatis-config.xml`）或者通过编程方式配置的 `Configuration` 对象来构建 `SqlSessionFactory` 实例。

### 基于xml构建

基于mybatis配置文件mybatis-config.xml构建SqlSessionFactory,需要读取类路径下的资源配置文件(src/main/resources/mybatis-config.xml)，mybatis提供了一个Resources工具类，可以更加简单读取。

```java
String resource = "mybatis-config.xml";
InputStream inputStream = Resources.getResourceAsStream(resource);
SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
```

**mybatis配置文件示例：**

```xml
<!-- mybatis-config.xml -->
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
    <environments default="development">
        <environment id="development">
            <transactionManager type="JDBC"/>
            <dataSource type="POOLED">
                <property name="driver" value="${driver}"/>
                <property name="url" value="${url}"/>
                <property name="username" value="${username}"/>
                <property name="password" value="${password}"/>
            </dataSource>
        </environment>
    </environments>
</configuration>
```

### 基于Configruation构建

基于自定义的mybatis全局配置类构建SqlSessionFactory，可以配置数据源、自定义连接池等相关配置信息。

```java
// 自定义配置 HikariCP 连接池
HikariConfig hikariConfig = new HikariConfig();
hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
hikariConfig.setJdbcUrl("jdbc:mysql://localhost:3306/test");
hikariConfig.setUsername("root");
hikariConfig.setPassword("123456");
DataSource dataSource = new HikariDataSource(hikariConfig);
TransactionFactory transactionFactory = new JdbcTransactionFactory();
Environment environment = new Environment("development", transactionFactory, dataSource);
Configuration configuration = new Configuration(environment);
SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
```

## 构建SqlSession

通过SqlSessionFactory创建SqlSession进行管理数据库连接和管理事务,采用以下代码示例创建SqlSession无需手动关闭连接。

```java
try (SqlSession sqlSession = sqlSessionFactory.getSqlSessionFactoryByXml().openSession()){
	//todo 业务代码
    // 提交事务
    sqlSession.commit();
}catch (Exception e) {
    // 捕获异常，回滚事务
    sqlSession.rollback();
    //处理异常
}
```

> 构建SqlSessionFactory有三种方式：
>
> 1. 纯读取mybatis-config.xml
> 2. 纯读取Configuration
> 3. 两种混合使用构建[由于Java注解的限制，一些复杂的sql不好实现，实际采用的是，配置文件+配置类+映射文件+注解的方式使用]

SqlSessionFactory创建SqlSession实例6种方法：

- **事务处理**：如何实现自动提交或者手动提交？
- **数据库连接**：如何使用其他自定义的数据源？
- **语句执行**：如何复用PreparedStatement执行语句或批量更新语句（包括插入语句和删除语句）？

基于以上需求，有下列已重载的多个 openSession() 方法供使用。

```java
SqlSession openSession(boolean autoCommit)
SqlSession openSession(Connection connection)
SqlSession openSession(TransactionIsolationLevel level)
SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level)
SqlSession openSession(ExecutorType execType, boolean autoCommit)
SqlSession openSession(ExecutorType execType, Connection connection)
```

默认的 openSession() 方法没有参数：

- 事务作用域将会开启（也就是不自动提交，如果未调用 `commit` 方法，`SqlSession` 关闭时 MyBatis 会自动回滚事务。）。
- 将由当前环境配置的 DataSource 实例中获取 Connection 对象。
- 事务隔离级别将会使用驱动或数据源的默认设置。
- 预处理语句不会被复用，也不会批量处理更新。

`ExecutorType` 这个枚举类型定义了三个值:

- `ExecutorType.SIMPLE`：为每个语句的执行创建一个新的预处理语句。
- `ExecutorType.REUSE`：该类型的执行器会复用预处理语句。
- `ExecutorType.BATCH`：该类型的执行器会批量执行所有更新语句，如果 SELECT 在多个更新中间执行，将在必要时将多条更新语句分隔开来，以方便理解。

**立即批量更新方法**

当你将 `ExecutorType` 设置为 `ExecutorType.BATCH` 时，可以使用这个方法清除（执行）缓存在 JDBC 驱动类中的批量更新语句。

```
if (count % batchSize == 0) {
     sqlSession.flushStatements(); // 手动刷新缓存中的 SQL 语句
     sqlSession.clearCache(); // 清空缓存，防止内存溢出
}
```

## 作用域和生命周期

### SqlSessionFactoryBuilder

创建完SqlSessionFactory就可以放弃了，属于方法作用域

### SqlSessionFactory

创建完了就一直存在，每次使用都拿去创建SqlSession对象，作用域是应用作用域，应该使用单例模式或者静态单例模式。

```java
public class MyBatisSessionFactory {
    private static final SqlSessionFactory sqlSessionFactory;
    static {
        // 1. 加载 MyBatis 配置文件
        String resource = "mybatis-config.xml";
        InputStream inputStream = MyBatisSessionFactory.class.getClassLoader().getResourceAsStream(resource);
        // 2. 构建 SqlSessionFactory
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    }
    // 私有构造方法，防止外部实例化
    private MyBatisSessionFactory() {
    }
    // 获取 SqlSessionFactory 的静态方法
    public static SqlSessionFactory getSqlSessionFactory() {
        return sqlSessionFactory;
    }
}
```

### SqlSession

每个现成都有自己的sqlSession,SqlSession不是线程安全的，不能被共享，作用域是方法作用域，每次操作完就自动关闭。

```java
try (SqlSession session = sqlSessionFactory.openSession()) {
  // todo
}
```

### Mapper

映射器是用来绑定映射语句的接口，映射器的实例是从SqlSession中获取的，使用完即可关闭，作用域是方法作用域。

```java
try (SqlSession session = sqlSessionFactory.openSession()) {
  BlogMapper mapper = session.getMapper(BlogMapper.class);
  // todo
}
```

# 配置文件

## mybatis配置文件结构

mybatis的配置文档的顶层结构如下

- configuration（配置）
  - properties（属性）
  - settings（设置）
  - typeAliases（类型别名）
  - typeHandlers（类型处理器）
  - objectFactory（对象工厂）
  - plugins（插件）
  - environments（环境配置）
    - environment（环境变量）
      - transactionManager（事务管理器）
      - dataSource（数据源）
  - databaseIdProvider（数据库厂商标识）
  - mappers（映射器）

## 属性

属性可以采用外部的配置文件，如jdbc.properties，将外部的配置文件引入后，可以在整个mybatis-config.xml配置文件中引用，也可以在构建`sqlSessionFactory`的时候传入自定义的配置的properties

### 加载外部配置文件

**外部配置文件jdbc.properties**

```properties
driver=com.mysql.cj.jdbc.Driver
url=jdbc:mysql://localhost:3306/mybatis_tutorial?serverTimezone=Asia/Shanghai
username=root
password=123456
```

**mybatis-config.xml引用属性**

```xml
<dataSource type="POOLED">
	<property name="driver" value="${driver}"/>
    <property name="url" value="${url}"/>
    <property name="username" value="${username}"/>
    <property name="password" value="${password}"/>
</dataSource>
```

### 构建SqlSessionFactor加载属性

```java
String resource = "mybatis-config.xml";
InputStream inputStream = Resources.getResourceAsStream(resource);
//自定义配置properties
Properties props = new Properties();
props.setProperty("driver", "com.mysql.cj.jdbc.Driver");
props.setProperty("url", "jdbc:mysql://localhost:3306/mybatis_tutorial?serverTimezone=Asia/Shanghai");
props.setProperty("username", "root");
props.setProperty("password", "123456");
SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream, props);
```

### 属性优先级

构建SqlSessionFactory传入的属性 > 加载外部配置文件的属性 > 在mybatis-config.xml中properties元素体内指定的属性

## 设置

```xml
<settings>
  <!-- 启用或禁用二级缓存 -->
  <setting name="cacheEnabled" value="true"/>
  
  <!-- 启用或禁用延迟加载 -->
  <setting name="lazyLoadingEnabled" value="true"/>
  
  <!-- 启用或禁用激进的延迟加载 -->
  <setting name="aggressiveLazyLoading" value="true"/>
  
  <!-- 启用或禁用多结果集支持 -->
  <setting name="multipleResultSetsEnabled" value="true"/>
  
  <!-- 使用列标签代替列名 -->
  <setting name="useColumnLabel" value="true"/>
  
  <!-- 启用或禁用 JDBC 生成的键 -->
  <setting name="useGeneratedKeys" value="false"/>
  
  <!-- 自动映射行为，PARTIAL 表示部分映射 -->
  <setting name="autoMappingBehavior" value="PARTIAL"/>
  
  <!-- 未知列的自动映射行为，WARNING 表示警告 -->
  <setting name="autoMappingUnknownColumnBehavior" value="WARNING"/>
  
  <!-- 默认的执行器类型，SIMPLE 表示简单执行器 -->
  <setting name="defaultExecutorType" value="SIMPLE"/>
  
  <!-- 默认的语句超时时间（秒） -->
  <setting name="defaultStatementTimeout" value="25"/>
  
  <!-- 默认的获取大小 -->
  <setting name="defaultFetchSize" value="100"/>
  
  <!-- 启用或禁用安全的 RowBounds -->
  <setting name="safeRowBoundsEnabled" value="false"/>
  
  <!-- 启用或禁用安全的结果处理器 -->
  <setting name="safeResultHandlerEnabled" value="true"/>
  
  <!-- 启用或禁用下划线转驼峰命名法 -->
  <setting name="mapUnderscoreToCamelCase" value="false"/>
  
  <!-- 本地缓存范围，SESSION 表示会话级别缓存 -->
  <setting name="localCacheScope" value="SESSION"/>
  
  <!-- 为 NULL 值指定的 JDBC 类型 -->
  <setting name="jdbcTypeForNull" value="OTHER"/>
  
  <!-- 延迟加载触发的方法 -->
  <setting name="lazyLoadTriggerMethods" value="equals,clone,hashCode,toString"/>
  
  <!-- 默认的脚本语言驱动 -->
  <setting name="defaultScriptingLanguage" value="org.apache.ibatis.scripting.xmltags.XMLLanguageDriver"/>
  
  <!-- 默认的枚举类型处理器 -->
  <setting name="defaultEnumTypeHandler" value="org.apache.ibatis.type.EnumTypeHandler"/>
  
  <!-- 启用或禁用在设置 NULL 值时调用 setter 方法 -->
  <setting name="callSettersOnNulls" value="false"/>
  
  <!-- 启用或禁用返回空行的实例 -->
  <setting name="returnInstanceForEmptyRow" value="false"/>
  
  <!-- 日志前缀 -->
  <setting name="logPrefix" value="exampleLogPreFix_"/>
  
  <!-- 日志实现，SLF4J、LOG4J、LOG4J2、JDK_LOGGING、COMMONS_LOGGING、STDOUT_LOGGING、NO_LOGGING -->
  <setting name="logImpl" value="SLF4J"/>
  
  <!-- 代理工厂，CGLIB 或 JAVASSIST -->
  <setting name="proxyFactory" value="CGLIB"/>
  
  <!-- 自定义 VFS 实现 -->
  <setting name="vfsImpl" value="org.mybatis.example.YourselfVfsImpl"/>
  
  <!-- 启用或禁用使用实际的参数名称 -->
  <setting name="useActualParamName" value="true"/>
  
  <!-- 配置工厂类 -->
  <setting name="configurationFactory" value="org.mybatis.example.ConfigurationFactory"/>
</settings>
```

## 类型别名

类型别名可以给一个java类型设置一个缩写名字，他仅适用于xml配置，主要降低全限定类名的书写。

### 配置文件方式

```xml
<typeAliases>
  <typeAlias alias="Author" type="domain.blog.Author"/>
  <typeAlias alias="Blog" type="domain.blog.Blog"/>
  <typeAlias alias="Comment" type="domain.blog.Comment"/>
  <typeAlias alias="Post" type="domain.blog.Post"/>
  <typeAlias alias="Section" type="domain.blog.Section"/>
  <typeAlias alias="Tag" type="domain.blog.Tag"/>
</typeAliases>
```

### 注解方式

typeAlias指定一个包的时候，默认会采用bean的名字首字母小写

```xml
<typeAliases>
  <package name="domain.blog"/>
</typeAliases>
```

```java
@Alias("Author")//不加注解默认是author,加了注解以后就是Author
public class Author {
    
}
```

## 类型处理器

MyBatis在设置预处理语句（PreparedStatement）中的参数或从结果集中取出一个值时，都会用类型处理器将获取到的值以合适的方式转换成 Java 类型，LocalDateTime与数据库中DateTime相互对应，mybatis会自动处理，但是有一些，例如：pojo是list,mysql中是varchar()这就要需要设置自定义类型处理来处理了。

### 配置类型处理器

实现 `org.apache.ibatis.type.TypeHandler` 接口或继承一个很便利的类 `org.apache.ibatis.type.BaseTypeHandler`，将它映射到一个 JDBC 类型。

```java
public class StringListTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, String.join(",", parameter));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String columnValue = rs.getString(columnName);
        return columnValue != null ? Arrays.asList(columnValue.split(",")) : null;
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String columnValue = rs.getString(columnIndex);
        return columnValue != null ? Arrays.asList(columnValue.split(",")) : null;
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String columnValue = cs.getString(columnIndex);
        return columnValue != null ? Arrays.asList(columnValue.split(",")) : null;
    }
}
```

### 注册到Mybatis

```xml
<!-- mybatis-config.xml -->
<typeHandlers>
     <typeHandler handler="com.tutorial.mybatis.handler.StringListTypeHandler" javaType="java.util.List"/>
</typeHandlers>
```

### 编写映射文件配置

```xml
<!-- BlogMapper.xml -->
<mapper namespace="com.tutorial.mybatis.mapper.BlogMapper">
    <resultMap id="BlogResultMap" type="com.tutorial.mybatis.pojo.Blog">
        ……
        <result property="tags" column="tags" typeHandler="com.tutorial.mybatis.handler.StringListTypeHandler"/>
        <result property="categories" column="categories" typeHandler="com.tutorial.mybatis.handler.StringListTypeHandler"/>
        ……
    </resultMap>
    <select id="selectBlog" resultMap="BlogResultMap">
        select * from Blog where id = #{id}
    </select>
</mapper>
```

## 对象工厂

>在 MyBatis 中，对象工厂（ObjectFactory）负责实例化所有映射的对象。它主要用于创建从数据库返回的结果对象。

### 默认对象工厂

默认情况下，MyBatis 使用 `DefaultObjectFactory` 来创建结果对象。这对于大多数情况已经足够了。

### 自定义对象工厂

通过实现 `ObjectFactory` 接口来自定义对象工厂。假设我们有一个 `User` 类，我们希望在创建 `User` 对象时自动设置一个默认的状态字段。

**1. User类**

```java
@Data
public class ObjectFactoryUser implements Serializable {
    private int id;
    private String name;
    private String status;
}
```

**2. 自定义对象工厂，给ObjectFactoryUser设置默认的状态为 "active"。**

```java
public class CustomObjectFactory extends DefaultObjectFactory {

    @Override
    public <T> T create(Class<T> type) {
        T instance = super.create(type);
        initialize(instance);
        return instance;
    }

    @Override
    public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        T instance = super.create(type, constructorArgTypes, constructorArgs);
        initialize(instance);
        return instance;
    }

    @Override
    public void setProperties(Properties properties) {
        super.setProperties(properties);
    }

    private <T> void initialize(T instance) {
        if (instance instanceof User) {
            ((User) instance).setStatus("active");
        }
    }
}

```

**3. 配置自定义对象工厂**

```xml
<!-- mybatis-config.xml -->
<configuration>
    <!-- 其他配置 -->
    <objectFactory type="com.example.CustomObjectFactory"/>
</configuration>
```

当 `ObjectFactoryUser` 对象通过MyBatis查询创建时，自定义的对象工厂会自动将`status` 字段设置为 "active"。输出结果应显示用户的状态为 "active"。

**作用：**

1. **创建结果对象**：当 MyBatis 从数据库返回结果集时，使用对象工厂创建结果对象实例。

2. **对象初始化**：可以在对象创建时进行一些自定义的初始化工作，比如依赖注入、默认值设置等。

3. **构造函数选择**：可以选择使用特定的构造函数来创建对象，满足特殊的对象创建需求。

## 插件

### 开发步骤

1. **实现 Interceptor 接口**：创建一个类实现 MyBatis 提供的 `Interceptor` 接口。
2. **注入插件**：在 MyBatis 配置文件中注入插件。
3. **配置插件**：在插件类中配置需要拦截的方法和自定义逻辑。

### 插件原理

MyBatis 插件通过拦截器机制，在执行 SQL 语句的四个主要方法前后进行拦截：

- `Executor`：执行增删改查操作的方法。
- `ParameterHandler`：处理参数的方法。
- `ResultSetHandler`：处理结果集的方法。
- `StatementHandler`：处理 SQL 语句的方法。

通过拦截这些方法，可以在执行 SQL 语句前后进行自定义操作。

### 插件示例

```java
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class SqlExecutionTimeInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory.getLogger(SqlExecutionTimeInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 执行被拦截的方法
        Object result = invocation.proceed();

        long endTime = System.currentTimeMillis();
        logger.info("SQL execution took " + (endTime - startTime) + " ms");
        return result;
    }

    @Override
    public Object plugin(Object target) {
        // 创建目标对象的代理
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 读取插件配置的属性
    }
}
```

**注入插件**

```xml
 <plugins>
        <plugin interceptor="com.tutorial.mybatis.plugin.SqlExecutionTimeInterceptor">
        </plugin>
 </plugins>
```

### 插件配置说明

- `@Intercepts` 注解：用于定义插件要拦截的方法。
- `@Signature` 注解：用于指定要拦截的类、方法和参数类型。
- `intercept` 方法：插件的核心逻辑，拦截目标方法并执行自定义操作。
- `plugin` 方法：用于创建插件的代理对象。
- `setProperties` 方法：用于设置插件的属性。

### 插件开发原理

- **拦截器机制**：MyBatis 插件通过拦截器机制，在执行 SQL 语句的四个主要方法前后进行拦截。通过 `@Intercepts` 和 `@Signature` 注解，可以指定要拦截的类、方法和参数类型。
- **代理模式**：MyBatis 插件使用代理模式，通过 `Plugin.wrap` 方法创建目标对象的代理对象。在代理对象中，可以在执行目标方法前后进行自定义操作。
- **反射机制**：在 `intercept` 方法中，可以通过反射机制获取目标对象和方法参数，从而实现自定义操作。

### 分页插件

**1. 导入分页插件依赖**

```xml
<!-- PageHelper 依赖 -->
<dependency>
    <groupId>com.github.pagehelper</groupId>
    <artifactId>pagehelper</artifactId>
    <version>5.3.1</version>
</dependency>
```

**2. 注入插件**

```xml
<configuration>
    <plugins>
        <!-- 配置 PageHelper 插件 -->
        <plugin interceptor="com.github.pagehelper.PageInterceptor">
            <!-- 指定数据库方言，这里设置为 MySQL -->
            <property name="helperDialect" value="mysql"/>
            <!-- 启用分页参数合理化 -->
            <!-- 当页码参数小于 1 时，会自动调整为 1 -->
            <!-- 当页码大于最大页数时，会自动调整为最大页数 -->
            <property name="reasonable" value="true"/>
            <!-- 支持从方法参数中获取分页参数 -->
            <!-- 允许在 Mapper 接口的方法参数中直接传递分页参数 -->
            <property name="supportMethodsArguments" value="true"/>
            <!-- 配置额外的分页参数 -->
            <!-- 使用 countSql 作为统计总记录数的 SQL 语句 -->
            <property name="params" value="count=countSql"/>
        </plugin>
    </plugins>
</configuration>
```

**3. 测试分页插件**

```java
@Test
public void testPlugin() throws IOException {
        BuildSqlSessionFactory sqlSessionFactory = new BuildSqlSessionFactory();
        try (SqlSession sqlSession = sqlSessionFactory.getSqlSessionFactoryByXml().openSession()){
            FoodMapper mapper = sqlSession.getMapper(FoodMapper.class);
            // 查询第一页，每页 2 条记录
            PageHelper.startPage(2, 2);
            Page<Food> foods = mapper.selectAllFood();
            PageInfo<Food> pageInfo = new PageInfo<>(foods);

            // 输出食物信息
            List<Food> foodList = pageInfo.getList();
            foodList.forEach(System.out::println);

            // 输出分页信息
            System.out.println("当前页: " + pageInfo.getPageNum());
            System.out.println("每页的记录数: " + pageInfo.getPageSize());
            System.out.println("总记录数: " + pageInfo.getTotal());
            System.out.println("总页数: " + pageInfo.getPages());

        }
}
```

## 环境配置

生产过程中可能会涉及多个环境，如开发环境、测试环境、生产环境

### 配置文件

```xml
	<!-- 定义多个环境 -->
    <environments default="development">
        <!-- 开发环境 -->
        <environment id="development">
            <transactionManager type="JDBC"/>
            <dataSource type="POOLED">
                <property name="driver" value="com.mysql.cj.jdbc.Driver"/>
                <property name="url" value="jdbc:mysql://localhost:3306/dev_db"/>
                <property name="username" value="dev_user"/>
                <property name="password" value="dev_password"/>
            </dataSource>
        </environment>
        <!-- 测试环境 -->
        <environment id="test">
            <transactionManager type="JDBC"/>
            <dataSource type="POOLED">
                <property name="driver" value="com.mysql.cj.jdbc.Driver"/>
                <property name="url" value="jdbc:mysql://localhost:3306/test_db"/>
                <property name="username" value="test_user"/>
                <property name="password" value="test_password"/>
            </dataSource>
        </environment>
        <!-- 生产环境 -->
        <environment id="production">
            <transactionManager type="JDBC"/>
            <dataSource type="POOLED">
                <property name="driver" value="com.mysql.cj.jdbc.Driver"/>
                <property name="url" value="jdbc:mysql://localhost:3306/prod_db"/>
                <property name="username" value="prod_user"/>
                <property name="password" value="prod_password"/>
            </dataSource>
        </environment>
    </environments>
```

### 按需使用环境

```java
public SqlSessionFactory getSqlSessionFactory() throws IOException {
        String resource = "mybatis-config.xml";
        InputStream inputStream = Resources.getResourceAsStream(resource);
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream,"{environment id}");
        return sqlSessionFactory;
}
```

## 事务管理器

 MyBatis 中有两种类型的事务管理器（也就是 type="[JDBC|MANAGED]"）

- JDBC – 这个配置直接使用了 JDBC 的提交和回滚功能，它依赖从数据源获得的连接来管理事务作用域。默认情况下，为了与某些驱动程序兼容，它在关闭连接时启用自动提交。然而，对于某些驱动程序来说，启用自动提交不仅是不必要的，而且是一个代价高昂的操作。因此，从 3.5.10 版本开始，你可以通过将 "skipSetAutoCommitOnClose" 属性设置为 "true" 来跳过这个步骤。例如：

  ```
  <transactionManager type="JDBC">
    <property name="skipSetAutoCommitOnClose" value="true"/>
  </transactionManager>
  ```

- MANAGED – 这个配置几乎没做什么。它从不提交或回滚一个连接，而是让容器来管理事务的整个生命周期（比如 JEE 应用服务器的上下文）。 默认情况下它会关闭连接。然而一些容器并不希望连接被关闭，因此需要将 closeConnection 属性设置为 false 来阻止默认的关闭行为。例如:

  ```
  <transactionManager type="MANAGED">
    <property name="closeConnection" value="false"/>
  </transactionManager>
  ```

**提示** Spring + MyBatis，则没有必要配置事务管理器，因为 Spring 模块会使用自带的管理器来覆盖前面的配置。

## 数据源

三种内建的数据源类型（也就是 type="[UNPOOLED|POOLED|JNDI]"）：

**UNPOOLED**– 这个数据源的实现会每次请求时打开和关闭连接。

**POOLED**– 这种数据源的实现利用“池”的概念将 JDBC 连接对象组织起来，避免了创建新的连接实例时所必需的初始化和认证时间。默认使用

**JNDI** – 这个数据源实现是为了能在如 EJB 或应用服务器这类容器中使用，容器可以集中或在外部配置数据源，然后放置一个 JNDI 上下文的数据源引用。

### 添加自定义数据源HikariCP

**1. 导入依赖**

```xml
<!-- HikariCP -->
<dependency>
   <groupId>com.zaxxer</groupId>
   <artifactId>HikariCP</artifactId>
   <version>4.0.3</version>
</dependency>
```

**2. 创建自定义的 HikariCP 数据源工厂**

在 MyBatis 中使用 HikariCP 作为数据源时，创建自定义的 HikariCP 数据源工厂是为了满足 MyBatis 的数据源配置要求。MyBatis 期望的数据源配置类需要实现 `DataSourceFactory` 接口，而 HikariCP 本身并不直接实现这个接口。因此，我们需要创建一个自定义的工厂类来适配 HikariCP 数据源。

```java
public class HikariCPDataSourceFactory implements DataSourceFactory {
    private Properties properties;

    @Override
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    @Override
    public DataSource getDataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(properties.getProperty("driverClassName"));
        config.setJdbcUrl(properties.getProperty("jdbcUrl"));
        config.setUsername(properties.getProperty("username"));
        config.setPassword(properties.getProperty("password"));
        return new HikariDataSource(config);
    }
}
```

**3. 配置 MyBatis 使用自定义的数据源工厂**

```xml
<dataSource type="com.tutorial.mybatis.factory.HikariCPDataSourceFactory">
     <property name="driverClassName" value="${driver}"/>
     <property name="jdbcUrl" value="${url}"/>
     <property name="username" value="${username}"/>
     <property name="password" value="${password}"/>
</dataSource>
```

## 数据库厂商

MyBatis 可以根据不同的数据库厂商执行不同的语句，这种多厂商的支持是基于映射语句中的 `databaseId` 属性。 MyBatis 会加载带有匹配当前数据库 `databaseId` 属性和所有不带 `databaseId` 属性的语句。 如果同时找到带有 `databaseId` 和不带 `databaseId` 的相同语句，则后者会被舍弃。 为支持多厂商特性，只要像下面这样在 mybatis-config.xml 文件中加入 `databaseIdProvider` 即可：

```
<databaseIdProvider type="DB_VENDOR" />
```

databaseIdProvider 对应的 DB_VENDOR 实现会将 databaseId 设置为 `DatabaseMetaData#getDatabaseProductName()` 返回的字符串。 由于通常情况下这些字符串都非常长，而且相同产品的不同版本会返回不同的值，你可能想通过设置属性别名来使其变短：

```
<databaseIdProvider type="DB_VENDOR">
  <property name="SQL Server" value="sqlserver"/>
  <property name="DB2" value="db2"/>
  <property name="Oracle" value="oracle" />
</databaseIdProvider>
```

在提供了属性别名时，databaseIdProvider 的 DB_VENDOR 实现会将 databaseId 设置为数据库产品名与属性中的名称第一个相匹配的值，如果没有匹配的属性，将会设置为 “null”。 在这个例子中，如果 `getDatabaseProductName()` 返回“Oracle (DataDirect)”，databaseId 将被设置为“oracle”。

你可以通过实现接口 `org.apache.ibatis.mapping.DatabaseIdProvider` 并在 mybatis-config.xml 中注册来构建自己的 DatabaseIdProvider：

```
public interface DatabaseIdProvider {
  default void setProperties(Properties p) { // 从 3.5.2 开始，该方法为默认方法
    // 空实现
  }
  String getDatabaseId(DataSource dataSource) throws SQLException;
}
```

## 映射器

四种映射器方式，可以找到对应配置的映射文件

```xml
<!-- 使用相对于类路径的资源引用 -->
<mappers>
        <mapper resource="com.tutorial.mybatis.mapper/BlogMapper.xml"/>
        <mapper resource="com.tutorial.mybatis.mapper/UserMapper.xml"/>
        <mapper resource="com.tutorial.mybatis.mapper/OrderMapper.xml"/>
        <mapper resource="com.tutorial.mybatis.mapper/BookMapper.xml"/>
        <mapper resource="com.tutorial.mybatis.mapper/AnimalMapper.xml"/>
        <mapper resource="com.tutorial.mybatis.mapper/FoodMapper.xml"/>
</mappers>
```

```xml
<!-- 使用映射器接口实现类的完全限定类名 -->
 <mappers>
        <mapper class="com.tutorial.mybatis.mapper.BlogMapper"/>
        <mapper class="com.tutorial.mybatis.mapper.UserMapper"/>
        <mapper class="com.tutorial.mybatis.mapper.OrderMapper"/>
        <mapper class="com.tutorial.mybatis.mapper.BookMapper"/>
        <mapper class="com.tutorial.mybatis.mapper.AnimalMapper"/>
        <mapper class="com.tutorial.mybatis.mapper.FoodMapper"/>
</mappers>
```

```xml
<!-- 将包内的映射器接口全部注册为映射器 -->
<mappers>
  <package name="com.tutorial.mybatis.mapper"/>
</mappers>
```

# XML 映射器

SQL 映射文件只有很少的几个顶级元素（按照应被定义的顺序列出）：

- `cache` – 该命名空间的缓存配置。
- `cache-ref` – 引用其它命名空间的缓存配置。
- `resultMap` – 描述如何从数据库结果集中加载对象，是最复杂也是最强大的元素。
- `sql` – 可被其它语句引用的可重用语句块。
- `insert` – 映射插入语句。
- `update` – 映射更新语句。
- `delete` – 映射删除语句。
- `select` – 映射查询语句。

## select

```xml
<select id="selectBlog" resultMap="BlogResultMap">
        select * from Blog where id = #{id}
</select>
<!-- 常见参数含义 -->
<select
  id="selectBlog"              <!-- SQL 语句的唯一标识符，用于在 MyBatis 中引用这个语句 -->
  parameterType="int"            <!-- 输入参数的类型，这里是 int 类型 -->
  resultType="hashmap"           <!-- 结果集的类型，这里是 HashMap -->
  resultMap="BlogResultMap"    <!-- 结果映射的 ID，用于复杂的结果集映射 -->
  flushCache="false"             <!-- 是否在执行该语句时刷新缓存，默认值为 false -->
  useCache="true"                <!-- 是否启用二级缓存，默认值为 true -->
  timeout="10"                   <!-- SQL 语句的超时时间，单位为秒 -->
  fetchSize="256"                <!-- 提示驱动程序每次批量返回的行数 -->
  statementType="PREPARED"       <!-- SQL 语句的类型，可以是 STATEMENT、PREPARED 或 CALLABLE -->
  resultSetType="FORWARD_ONLY">  <!-- 结果集的类型，可以是 FORWARD_ONLY、SCROLL_SENSITIVE 或 SCROLL_INSENSITIVE -->
```

## insert, update 和 delete

```xml
<insert
  id="insertAuthor"              <!-- SQL 语句的唯一标识符，用于在 MyBatis 中引用这个语句 -->
  parameterType="domain.blog.Author"  <!-- 输入参数的类型，这里是 domain.blog.Author 类 -->
  flushCache="true"              <!-- 是否在执行该语句时刷新缓存，默认值为 true -->
  statementType="PREPARED"       <!-- SQL 语句的类型，可以是 STATEMENT、PREPARED 或 CALLABLE，默认值为 PREPARED -->
  keyProperty=""                 <!-- （可选）指定生成的主键将被设置到的属性名 -->
  keyColumn=""                   <!-- （可选）指定数据库中生成的主键列名 -->
  useGeneratedKeys=""            <!-- （可选）是否使用 JDBC 的 getGeneratedKeys 方法获取主键，默认值为 false -->
  timeout="20">                  <!-- SQL 语句的超时时间，单位为秒，默认值取决于数据库驱动程序 -->
    <!-- SQL 插入语句 -->
</insert>

<update
  id="updateAuthor"              <!-- SQL 语句的唯一标识符，用于在 MyBatis 中引用这个语句 -->
  parameterType="domain.blog.Author"  <!-- 输入参数的类型，这里是 domain.blog.Author 类 -->
  flushCache="true"              <!-- 是否在执行该语句时刷新缓存，默认值为 true -->
  statementType="PREPARED"       <!-- SQL 语句的类型，可以是 STATEMENT、PREPARED 或 CALLABLE，默认值为 PREPARED -->
  timeout="20">                  <!-- SQL 语句的超时时间，单位为秒，默认值取决于数据库驱动程序 -->
    <!-- SQL 更新语句 -->
</update>

<delete
  id="deleteAuthor"              <!-- SQL 语句的唯一标识符，用于在 MyBatis 中引用这个语句 -->
  parameterType="domain.blog.Author"  <!-- 输入参数的类型，这里是 domain.blog.Author 类 -->
  flushCache="true"              <!-- 是否在执行该语句时刷新缓存，默认值为 true -->
  statementType="PREPARED"       <!-- SQL 语句的类型，可以是 STATEMENT、PREPARED 或 CALLABLE，默认值为 PREPARED -->
  timeout="20">                  <!-- SQL 语句的超时时间，单位为秒，默认值取决于数据库驱动程序 -->
    <!-- SQL 删除语句 -->
</delete>
```

## SQL

定义可重用的 SQL 代码片段，以便在其它语句中使用

```xml
<sql id="userColumns"> ${alias}.id,${alias}.username,${alias}.password </sql>

<select id="selectUsers" resultType="map">
  select
    <include refid="userColumns"><property name="alias" value="t1"/></include>,
    <include refid="userColumns"><property name="alias" value="t2"/></include>
  from some_table t1
    cross join some_table t2
</select>
```

## 参数

对于大多数简单的使用场景，都不需要使用复杂的参数

```xml
<select id="selectUsers" resultType="User">
  select id, username, password
  from users
  where id = #{id}
</select>
```

 JDBC 要求，如果一个列允许使用 null 值，并且会使用值为 null 的参数，就必须要指定 JDBC 类型（jdbcType)。

## 字符串替换

默认情况下，使用 `#{}` 参数语法时，MyBatis 会创建 `PreparedStatement` 参数占位符，并通过占位符安全地设置参数（就像使用 ? 一样）。

有时你就是想直接在 SQL 语句中直接插入一个不转义的字符串。 比如 ORDER BY 子句，这时候你可以

```sql
-- MyBatis 就不会修改或转义该字符串了,用这种方式接受用户的输入，并用作语句参数是不安全的，会导致潜在的 SQL 注入攻击
ORDER BY ${columnName}
```

## 结果映射

### resultType

```xml
<select id="selectUsers" resultType="com.someapp.model.User">
  select id, username, hashedPassword
  from some_table
  where id = #{id}
</select>
```

```java
package com.someapp.model;
public class User {
  private int id;
  private String username;
  private String hashedPassword;
}    
```

### resultMap

解决数据库列名和实体类属性名不一致。

```xml
<resultMap id="userResultMap" type="User">
  <id property="id" column="user_id" />
  <result property="username" column="user_name"/>
  <result property="password" column="hashed_password"/>
</resultMap>

<select id="selectUsers" resultMap="userResultMap">
  select user_id, user_name, hashed_password
  from some_table
  where id = #{id}
</select>
```

### association

`association`用于处理一对一的关系。它通常用于映射一个对象内部的另一个对象。

**示例**

我们有两个表：`User` 和 `Address`，其中每个用户都有一个地址。

**表结构**：

```sql
CREATE TABLE User (
    id INT PRIMARY KEY,
    name VARCHAR(50),
    address_id INT
);

CREATE TABLE Address (
    id INT PRIMARY KEY,
    street VARCHAR(100),
    city VARCHAR(50)
);
```

**实体类：**

```java
@Data
public class Address {
    private int id;
    private String street;
    private String city;
}

@Data
public class User {
    private int id;
    private String name;
    private Address address;
}
```

**mybatis映射文件**

```xml
<mapper namespace="com.tutorial.mybatis.mapper.UserMapper">
    <resultMap id="UserResultMap" type="com.tutorial.mybatis.pojo.User">
        <id property="id" column="id"/>
        <result property="name" column="name"/>
        <association property="address" javaType="com.tutorial.mybatis.pojo.Address">
            <id property="id" column="address_id"/>
            <result property="street" column="street"/>
            <result property="city" column="city"/>
        </association>
    </resultMap>

    <select id="selectUser" resultMap="UserResultMap">
        SELECT u.id, u.name, a.id AS address_id, a.street, a.city
        FROM User u
        LEFT JOIN Address a ON u.address_id = a.id
        WHERE u.id = #{id}
    </select>
</mapper>
```

- **property**：Java 对象中的属性名。
- **javaType**：关联的 Java 类型。
- **column**：数据库中的列名。
- **id** 和 **result**：分别用于映射主键和普通字段。

### collection

用于处理一对多的关系。它通常用于映射一个对象内部的集合。

**示例**

假设我们有两个表：`Order` 和 `OrderItem`，其中每个订单都有多个订单项。

**表结构**：

```sql
CREATE TABLE `Order` (
    id INT PRIMARY KEY,
    user_id INT
);

CREATE TABLE OrderItem (
    id INT PRIMARY KEY,
    order_id INT,
    product_name VARCHAR(50),
    quantity INT
);
```

**实体类**：

```java
public class Order {
    private int id;
    private int userId;
    private List<OrderItem> orderItems;
    // getters and setters
}

public class OrderItem {
    private int id;
    private int orderId;
    private String productName;
    private int quantity;
    // getters and setters
}
```

**MyBatis 映射文件**：

```xml
<mapper namespace="com.tutorial.mybatis.mapper.OrderMapper">
    <resultMap id="OrderResultMap" type="com.tutorial.mybatis.pojo.Order">
        <id property="id" column="id"/>
        <result property="userId" column="user_id"/>
        <collection property="orderItems" ofType="com.tutorial.mybatis.pojo.OrderItem">
            <id property="id" column="item_id"/>
            <result property="orderId" column="order_id"/>
            <result property="productName" column="product_name"/>
            <result property="quantity" column="quantity"/>
        </collection>
    </resultMap>

    <select id="selectOrder" resultMap="OrderResultMap">
        SELECT o.id, o.user_id, i.id AS item_id, i.order_id, i.product_name, i.quantity
        FROM `Order` o
        LEFT JOIN OrderItem i ON o.id = i.order_id
        WHERE o.id = #{id}
    </select>
</mapper>
```

- **property**：Java 对象中的集合属性名。
- **ofType**：集合中元素的 Java 类型。
- **column**：数据库中的列名。
- **id** 和 **result**：分别用于映射主键和普通字段。

### 构造函数

在 MyBatis 中，除了使用 `<result>` 元素将查询结果映射到 Java 对象的属性外，还可以使用构造函数来进行映射。

**示例**

我们有一个 `Book` 类，它只有一个带参数的构造函数：

```java
@Data
@AllArgsConstructor
public class Book {
    private int id;
    private String title;
    private String author;
    private double price;
}   
```

**数据库表结构**

```sql
CREATE TABLE books (
    id INT PRIMARY KEY,
    title VARCHAR(100),
    author VARCHAR(100),
    price DOUBLE
);
```

**Mybatis映射文件**

```xml
<mapper namespace="com.tutorial.mybatis.mapper.BookMapper">

    <resultMap id="BookResultMap" type="com.tutorial.mybatis.pojo.Books">
        <constructor>
            <idArg column="id" javaType="Integer" />
            <arg column="title" javaType="String"/>
            <arg column="author" javaType="String"/>
            <arg column="price" javaType="Double"/>
        </constructor>
    </resultMap>

    <select id="selectById" resultMap="BookResultMap">
        select id,title,author,price from books where id = #{id}
    </select>
</mapper>
```

**Mapper接口**

```java
public interface BookMapper {
    Books selectById(Integer id);
}
```

- 使用 `<constructor>` 元素来定义构造函数参数的映射。
- `<idArg>` 用于映射主键参数，`<arg>` 用于映射其他参数。

### 鉴别器

鉴别器（Discriminator）用于处理多态结果映射，即根据某个列的值动态地选择不同的结果映射。这在处理继承关系或需要根据某个列的值映射到不同的类时非常有用。

**示例**

假设我们有一个 `Animal` 表，其中包含不同类型的动物（如猫和狗）。我们希望根据 `type` 列的值将结果映射到不同的类（`Cat` 或 `Dog`）。

**表结构**

```sql
CREATE TABLE animals (
    id INT PRIMARY KEY,
    name VARCHAR(50),
    type VARCHAR(20),
    breed VARCHAR(50),
    color VARCHAR(50)
);
```

**实体类**

我们有一个基类 `Animal`，以及两个子类 `Cat` 和 `Dog`。

```java
@Data
@NoArgsConstructor
public abstract class Animal {
    private Integer id;
    private String name;
    public Animal(Integer id,String name){
        this.id = id;
        this.name = name;
    }
}

@Data
public class Cat extends Animal{
    String breed;
    String color;
    public Cat(Integer id, String name,String breed,String color) {
        super(id, name);
        this.breed = breed;
        this.color = color;
    }
}

@Data
public class Dog extends Animal{
    String breed;
    String color;

    public Dog(Integer id, String name,String breed,String color) {
        super(id, name);
        this.breed = breed;
        this.color = color;
    }
}
```

**Mapper接口**

```java
public interface AnimalMapper {
    List<Animal> selectAll();
}
```

**Mapper映射文件**

```xml
<mapper namespace="com.tutorial.mybatis.mapper.AnimalMapper">

    <resultMap id="AnimalResultMap" type="com.tutorial.mybatis.pojo.Animal">
        <id property="id" column="id"/>
        <result property="name" column="name"/>
        <discriminator javaType="String" column="type">
            <case value="cat" resultType="com.tutorial.mybatis.pojo.Cat">
                <result property="breed" column="breed"/>
                <result property="color" column="breed"/>
            </case>
            <case value="dog" resultType="com.tutorial.mybatis.pojo.Dog">
                <result property="breed" column="breed"/>
                <result property="color" column="breed"/>
            </case>

        </discriminator>
    </resultMap>

    <select id="selectAll" resultMap="AnimalResultMap">
        select * from animals
    </select>
</mapper>
```

- 使用 `<discriminator>` 元素来定义鉴别器。
- `javaType` 属性指定鉴别器列的 Java 类型。
- `column` 属性指定用于鉴别的列名。
- `<case>` 元素用于定义不同值对应的结果映射。

## 自动映射

简单的场景下，MyBatis 可以为你自动映射查询结果，MyBatis会获取结果中返回的列名并在 Java 类中查找相同名字的属性（忽略大小写），但如果遇到复杂的场景，你需要构建一个结果映射，一般java遵循驼峰命名，数据库遵循_分割单词，为了让这两种自动映射需要在配置文件中开启驼峰命名。

```xml
<!-- mybatis-config.xml --> 
<settings>
        <setting name="mapUnderscoreToCamelCase" value="true"/>
</settings>
```

有三种自动映射等级：

- `NONE` - 禁用自动映射。仅对手动映射的属性进行映射。
- `PARTIAL` - 对除在内部定义了嵌套结果映射（也就是连接的属性）以外的属性进行映射
- `FULL` - 自动映射所有属性。

## 缓存

MyBatis 提供了两种类型的缓存机制：一级缓存（本地缓存）和二级缓存。一级缓存是默认启用的，而二级缓存需要进行额外的配置。

### 一级缓存（本地缓存）

一级缓存是 SqlSession 级别的缓存，它在同一个 SqlSession 中有效。MyBatis 默认启用一级缓存。

```java
@Test
    public void testCacheLevel1() throws IOException {
        BuildSqlSessionFactoryByXml sqlSessionFactory = new BuildSqlSessionFactoryByXml();
        try (SqlSession sqlSession = sqlSessionFactory.getSqlSessionFactory().openSession()){
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            // 第一次查询，结果会被缓存
            User user1 = mapper.selectUser(1);
            System.out.println(user1.toString());

            // 第二次查询，MyBatis 会从缓存中获取结果，而不是再次查询数据库
            User user2 = mapper.selectUser(1);
            System.out.println(user2.toString());
            
        }
    }
```

### 二级缓存

二级缓存是 Mapper 映射级别的缓存，它在多个 SqlSession 之间共享。

#### mybatis-config.xml

要启用二级缓存，需要进行以下配置：

```xml
<configuration>
    <settings>
        <setting name="cacheEnabled" value="true"/>
    </settings>
</configuration>
```

#### 配置Mapper文件

在 Mapper XML 文件中启用二级缓存：

```xml
<mapper namespace="com.tutorial.mybatis.mapper.UserMapper">
    <cache/>  <!-- 开启2级缓存 -->
    <resultMap id="UserResultMap" type="com.tutorial.mybatis.pojo.User">
        <id property="id" column="id"/>
        <result property="name" column="name"/>
        <association property="address" javaType="com.tutorial.mybatis.pojo.Address">
            <id property="id" column="address_id"/>
            <result property="street" column="street"/>
            <result property="city" column="city"/>
        </association>
    </resultMap>
    <select id="selectUser" resultMap="UserResultMap">
        SELECT u.id, u.name, a.id AS address_id, a.street, a.city
        FROM User u
        LEFT JOIN Address a ON u.address_id = a.id
        WHERE u.id = #{id}
    </select>
</mapper>
```

#### 实体类实现 Serializable 接口

```java
@Data
public class User implements Serializable {
    private int id;
    private String name;
    private Address address;
}
```

#### 配置缓存策略

通过 `<cache>` 元素配置缓存策略，例如设置缓存大小、刷新间隔等

```xml
<cache
    eviction="LRU"
    flushInterval="60000"
    size="512"
    readOnly="true"/>
```

- **eviction**：缓存回收策略，默认值是 LRU（最近最少使用）。
- **flushInterval**：刷新间隔，单位是毫秒。
- **size**：缓存大小，表示可以缓存的对象数目。
- **readOnly**：只读属性，默认值是 `false`。

# 动态 SQL

MyBatis 动态 SQL 是 MyBatis 提供的一种强大功能，用于在运行时动态生成 SQL 语句。动态 SQL 语句可以根据不同的条件生成不同的 SQL 语句，从而提高 SQL 的灵活性和可维护性。

- if
- choose (when, otherwise)
- trim (where, set)
- foreach

## `<if>` 标签

根据用户的名字和id动态查询用户

```xml
<select id="selectUserByIdAndName" resultMap="UserResultMap">
        SELECT u.id, u.name, a.id AS address_id, a.street, a.city
        FROM User u
        LEFT JOIN Address a ON u.address_id = a.id
        WHERE 1 = 1
        <if test="id != null" >
            and u.id = #{id}
        </if>
        <if test="name != null" >
            and u.name = #{name}
        </if>
</select>
```

```java
@Test
public void testIfLabel() throws IOException {
        BuildSqlSessionFactoryByXml sqlSessionFactory = new BuildSqlSessionFactoryByXml();
        try (SqlSession sqlSession = sqlSessionFactory.getSqlSessionFactory().openSession()){
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            User user1 = mapper.selectUserByIdAndName(1,"John Doe");
            System.out.println(user1.toString());
            User user2 = mapper.selectUserByIdAndName(2,null);
            System.out.println(user2.toString());
    }
 }
```

## `<choose>`、`<when>` 和 `<otherwise>` 标签

`<choose>` 标签用于实现类似于 Java 中的 `switch` 语句的功能，`<when>` 和 `<otherwise>` 标签用于定义条件分支。根据不通条件查询用户

```xml
<select id="selectUserByIdOrName" resultMap="UserResultMap">
        SELECT u.id, u.name, a.id AS address_id, a.street, a.city
        FROM User u
        LEFT JOIN Address a ON u.address_id = a.id
        WHERE 1 = 1
        <choose>
            <when test="id != null">
                and u.id = #{id}
            </when>
            <when test="name != null">
                and u.name = #{name}
            </when>
            <otherwise>
                and 1 = 0
            </otherwise>
        </choose>
</select>
```

```java
@Test
    public void testChoiceLabel() throws IOException {
        BuildSqlSessionFactoryByXml sqlSessionFactory = new BuildSqlSessionFactoryByXml();
        try (SqlSession sqlSession = sqlSessionFactory.getSqlSessionFactory().openSession()){
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            User user1 = mapper.selectUserByIdOrName(1,"John Doe");
            System.out.println(user1.toString());
            User user2 = mapper.selectUserByIdOrName(null,null);
            System.out.println(user2 == null ? true : false);
        }
}
```



## `<trim>`、`<where>` 和 `<set>` 标签

- `<trim>` 标签用于去除多余的前缀或后缀。
- `<where>` 标签用于自动处理 `WHERE` 子句中的条件。
- `<set>` 标签用于自动处理 `UPDATE` 语句中的 `SET` 子句。

有一个 `Food` 表，希望根据不同的条件动态查询和更新食物信息。

```xml
<select id="selectFood" resultType="com.tutorial.mybatis.pojo.Food">
        SELECT id, name, category, price, available
        FROM foods
        <where>
            <trim prefixOverrides="AND | OR">

                <if test="name != null and name != ''">
                     OR AND name = #{name}  <!-- OR AND trim前缀标签会去除掉 -->
                </if>
                <if test="category != null and category != ''">
                    AND category = #{category}
                </if>
                <if test="price != null and price != ''">
                    AND price = #{price}
                </if>
                <if test="available != null and available != ''">
                    AND available = #{available}
                </if>
            </trim>
        </where>
    </select>

    <!-- 动态更新食物信息 -->
    <update id="updateFood">
        UPDATE foods
        <set>
            <trim suffixOverrides=",">
                <if test="name != null">
                    name = #{name},
                </if>
                <if test="category != null">
                    category = #{category},
                </if>
                <if test="price != null">
                    price = #{price},
                </if>
                <if test="available != null">
                    available = #{available},, <!-- ,, trim后缀标签会去除掉 -->
                </if>
            </trim>
        </set>
        WHERE id = #{id}
    </update>
```

1. `<trim>` 标签用于去除多余的前缀或后缀。
2. `<where>` 标签自动处理 `WHERE` 子句中的条件。如果没有任何条件，它会去掉多余的 `WHERE` 关键字。
3. `<set>` 标签用于自动处理 `UPDATE` 语句中的 `SET` 子句。

## `<foreach>` 标签

`<foreach>` 标签用于遍历集合，并生成相应的 SQL 片段。例如查询指定集合id的食物。

```xml
<select id="selectFoodListById" resultType="com.tutorial.mybatis.pojo.Food">
        SELECT id, name, category, price, available
        FROM foods
        where id in 
        <foreach item="id" index="index" collection="listId" open="(" separator="," close=")">
            #{id}
        </foreach>
</select>
```

- collection="list"：指定要遍历的集合是 `list`。使用@param注解指定
- item="id"：指定集合中每个元素的变量名为 `id`。
- index="index"：指定集合中每个元素的索引变量名为 `index`（可选）。
- open="("：指定生成的 SQL 片段的开头部分为 `(`。
- close=")"：指定生成的 SQL 片段的结尾部分为 `)`。
- separator=","：指定生成的 SQL 片段中每个元素之间的分隔符为 `,`。

```java
@Test
public void testForeachLabel() throws IOException {
        BuildSqlSessionFactoryByXml sqlSessionFactory = new BuildSqlSessionFactoryByXml();
        try (SqlSession sqlSession = sqlSessionFactory.getSqlSessionFactory().openSession()){
            FoodMapper mapper = sqlSession.getMapper(FoodMapper.class);
            List<Food> apple = mapper.selectFoodListById(Arrays.asList(1,2,3,4,5,6));
            apple.forEach(System.out::println);
        }
 }
```

# SQL语句构建器

Java 程序员面对的最痛苦的事情之一就是在 Java 代码中嵌入 SQL 语句。这通常是因为需要动态生成 SQL 语句，不然我们可以将它们放到外部文件或者存储过程中。如你所见，MyBatis 在 XML 映射中具备强大的 SQL 动态生成能力。但有时，我们还是需要在 Java 代码里构建 SQL 语句。此时，MyBatis 有另外一个特性可以帮到你，让你从处理典型问题中解放出来，比如加号、引号、换行、格式化问题、嵌入条件的逗号管理及 AND 连接。确实，在 Java 代码中动态生成 SQL 代码真的就是一场噩梦。例如：

```java
String sql = "SELECT P.ID, P.USERNAME, P.PASSWORD, P.FULL_NAME, "
"P.LAST_NAME,P.CREATED_ON, P.UPDATED_ON " +
"FROM PERSON P, ACCOUNT A " +
"INNER JOIN DEPARTMENT D on D.ID = P.DEPARTMENT_ID " +
"INNER JOIN COMPANY C on D.COMPANY_ID = C.ID " +
"WHERE (P.ID = A.ID AND P.FIRST_NAME like ?) " +
"OR (P.LAST_NAME like ?) " +
"GROUP BY P.ID " +
"HAVING (P.LAST_NAME like ?) " +
"OR (P.FIRST_NAME like ?) " +
"ORDER BY P.ID, P.FULL_NAME";
```

## 解决方案

MyBatis 3 提供了方便的工具类来帮助解决此问题。借助 SQL 类，我们只需要简单地创建一个实例，并调用它的方法即可生成 SQL 语句。让我们来用 SQL 类重写上面的例子：

```java
private String selectPersonSql() {
  return new SQL() {{
    SELECT("P.ID, P.USERNAME, P.PASSWORD, P.FULL_NAME");
    SELECT("P.LAST_NAME, P.CREATED_ON, P.UPDATED_ON");
    FROM("PERSON P");
    FROM("ACCOUNT A");
    INNER_JOIN("DEPARTMENT D on D.ID = P.DEPARTMENT_ID");
    INNER_JOIN("COMPANY C on D.COMPANY_ID = C.ID");
    WHERE("P.ID = A.ID");
    WHERE("P.FIRST_NAME like ?");
    OR();
    WHERE("P.LAST_NAME like ?");
    GROUP_BY("P.ID");
    HAVING("P.LAST_NAME like ?");
    OR();
    HAVING("P.FIRST_NAME like ?");
    ORDER_BY("P.ID");
    ORDER_BY("P.FULL_NAME");
  }}.toString();
}
```



# 日志

## 日志架构

Mybatis内置了slf4j日志门面，我们采用slf4j+logback的架构

## 导入依赖

```pom.xml
<!-- pom.xml -->
<!-- SLF4J API -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>1.7.30</version>
</dependency>
<!-- Logback Classic -->
<dependency>
     <groupId>ch.qos.logback</groupId>
     <artifactId>logback-classic</artifactId>
     <version>1.2.3</version>
</dependency>
```

## 开启配置

```mybatis.xml
<!-- mybatis.xml 开启日志配置 -->
<settings>
	<setting name="logImpl" value="SLF4J"/>
</settings>
```

## 日志配置

```logback.xml
<!-- src/main/resources/logback.xml 控制台日志-->
<configuration>
    <!-- Console Appender -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- Root Logger -->
    <root level="debug">
        <appender-ref ref="STDOUT" />
    </root>
    <!-- MyBatis Logger -->
    <logger name="com.tutorial.mybatis" level="debug" />
</configuration>
```


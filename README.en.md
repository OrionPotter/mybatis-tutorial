<div align="right">

[中文](./README.md) | [English](./README.en.md)

</div>

# What Is MyBatis

> MyBatis is a persistence framework that supports custom SQL, stored procedures, and advanced mappings. It reduces the JDBC boilerplate for setting parameters and processing result sets, and maps Java primitive types, interfaces, and POJOs to database records through XML or annotations.

## Core Components

1. `SqlSessionFactory`: the factory used to create `SqlSession` instances.
2. `SqlSession`: the primary interface for interacting with the database, including connection and transaction management.
3. `Mapper`: the mapper interface defines database operations. SQL is bound by annotations or XML. When a mapper method is invoked, the dynamic proxy delegates the call to the executor to run the corresponding SQL.
4. `Configuration`: the global MyBatis configuration object that contains data sources, transaction managers, mappers, plugins, and other settings.
5. `Executor`: responsible for executing SQL statements and mapping result sets.

# Getting Started

## Install MyBatis

This project is built with Maven. You only need to add the MyBatis and MySQL connector dependencies in `pom.xml`. The examples in this project use MySQL.

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

## Build `SqlSessionFactory`

The core of MyBatis is `SqlSessionFactory`, which creates and manages `SqlSession` objects. To obtain a `SqlSessionFactory` instance, use `SqlSessionFactoryBuilder`. It can build the factory either from an XML configuration file such as `mybatis-config.xml` or from a programmatically constructed `Configuration` object.

### Build From XML

You can build `SqlSessionFactory` from the MyBatis configuration file `mybatis-config.xml`. Read the resource from the classpath, for example `src/main/resources/mybatis-config.xml`. MyBatis provides the `Resources` utility class to simplify reading the file.

```java
String resource = "mybatis-config.xml";
InputStream inputStream = Resources.getResourceAsStream(resource);
SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
```

**Example MyBatis configuration file:**

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

### Build From `Configuration`

You can also build `SqlSessionFactory` from a custom MyBatis global configuration class, which allows you to configure data sources, custom connection pools, and related settings in code.

```java
// Custom configuration with the HikariCP connection pool
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

## Build `SqlSession`

Create a `SqlSession` through `SqlSessionFactory` to manage database connections and transactions. Using a try-with-resources block means you do not need to close the connection manually.

```java
try (SqlSession sqlSession = sqlSessionFactory.getSqlSessionFactoryByXml().openSession()){
    // todo business code
    // Commit the transaction
    sqlSession.commit();
}catch (Exception e) {
    // Roll back on exception
    sqlSession.rollback();
    // handle exception
}
```

There are three common ways to build `SqlSessionFactory`:

1. Read only `mybatis-config.xml`
2. Read only `Configuration`
3. Combine both approaches. In real projects, a mixed approach is common because some complex SQL is not convenient to express with Java annotations alone

`SqlSessionFactory` provides six overloaded `openSession()` methods. They mainly differ in transaction handling, custom database connections, and statement execution strategies such as prepared statement reuse and batch updates.

```java
SqlSession openSession(boolean autoCommit)
SqlSession openSession(Connection connection)
SqlSession openSession(TransactionIsolationLevel level)
SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level)
SqlSession openSession(ExecutorType execType, boolean autoCommit)
SqlSession openSession(ExecutorType execType, Connection connection)
```

The default `openSession()` method has no parameters:

- A transaction scope is opened, so auto-commit is disabled.
- A `Connection` object is acquired from the configured data source.
- The default transaction isolation level is used.
- The executor type is `ExecutorType.SIMPLE`.

## Scope And Lifecycle

### `SqlSessionFactoryBuilder`

This builder should exist only while building a `SqlSessionFactory`. After the factory is created, the builder is no longer needed.

### `SqlSessionFactory`

`SqlSessionFactory` should exist for the lifetime of the application. It is usually created once and reused globally.

### `SqlSession`

`SqlSession` is not thread-safe. It should be created per request or per method execution and closed immediately after use.

### `Mapper`

Mapper instances are acquired from `SqlSession`. Their scope should be the same as the `SqlSession` that created them.

# Configuration File

## Structure Of The MyBatis Configuration File

The MyBatis configuration file usually contains the following parts:

- `properties`
- `settings`
- `typeAliases`
- `typeHandlers`
- `objectFactory`
- `plugins`
- `environments`
- `databaseIdProvider`
- `mappers`

## Properties

The `properties` element is used to externalize database connection information and other configurable values.

### Load An External Properties File

You can load external configuration values through the `resource` or `url` attributes.

### Load Properties While Building `SqlSessionFactory`

Properties can also be passed directly when building `SqlSessionFactory`.

### Property Priority

When the same key appears multiple times, the general priority is:

1. Properties passed programmatically
2. Properties loaded from external resources
3. Properties declared inline in the configuration file

## Settings

The `settings` element controls MyBatis runtime behavior, such as lazy loading, cache behavior, automatic mapping, and naming conventions.

## Type Aliases

Type aliases let you use short names instead of fully qualified Java class names.

### Configure In XML

Aliases can be declared explicitly or scanned by package in the configuration file.

### Configure With Annotations

You can also define aliases with annotations on Java classes.

## Type Handlers

Type handlers define how Java types are converted to JDBC types and vice versa.

### Configure Type Handlers

Register type handlers in the global configuration file.

### Register In MyBatis

You can register handlers by class, Java type, JDBC type, or package scanning.

### Configure In Mapper XML

Mapper files can reference a custom type handler for specific fields or parameters.

## Object Factory

The object factory controls how result objects are instantiated.

### Default Object Factory

By default, MyBatis uses `DefaultObjectFactory`.

### Custom Object Factory

You can implement your own object factory to customize instance creation behavior.

## Plugins

Plugins allow you to intercept the internal execution flow of MyBatis.

### Development Steps

1. Implement the `Interceptor` interface
2. Use `@Intercepts` and `@Signature` to declare interception points
3. Configure the plugin in MyBatis

### How Plugins Work

MyBatis wraps target objects with dynamic proxies and invokes interceptor logic before or after the underlying method call.

### Plugin Example

This project includes a sample SQL execution time interceptor.

### Plugin Configuration Notes

Plugins can receive custom properties from the MyBatis configuration file.

### Plugin Internals

Typical interception targets include `Executor`, `StatementHandler`, `ParameterHandler`, and `ResultSetHandler`.

### Paging Plugin

Pagination plugins typically intercept SQL execution, rewrite the original SQL, and add paging logic.

## Environment Configuration

The `environments` element is used to define multiple runtime environments, for example development, test, and production.

### Configuration File

Each environment usually declares a transaction manager and a data source.

### Use Environments On Demand

When building `SqlSessionFactory`, you can specify which environment to use.

## Transaction Manager

Common transaction manager types include `JDBC` and `MANAGED`.

## Data Source

MyBatis supports `UNPOOLED`, `POOLED`, and `JNDI` data sources by default.

### Add The Custom HikariCP Data Source

This project also demonstrates how to integrate a custom HikariCP data source factory.

## Database Vendor

`databaseIdProvider` can be used to distinguish SQL statements for different database vendors.

## Mappers

Mapper registration can be done by resource path, URL, class, or package scanning.

# XML Mappers

## `select`

The `select` statement is used for query operations.

## `insert`, `update`, And `delete`

These statements are used for data modification operations.

## SQL

Reusable SQL fragments can be extracted with the `sql` tag and included with `include`.

## Parameters

MyBatis supports simple types, objects, maps, and collections as input parameters.

## String Substitution

`${}` performs direct string substitution. `#{}` uses prepared statement parameters. In practice, `#{}` is usually the safer default.

## Result Mapping

Result mapping is one of the most important features in MyBatis.

### `resultType`

Use `resultType` when the query result can be mapped directly to a simple type or a Java object.

### `resultMap`

Use `resultMap` when you need custom field mapping, nested mapping, or complex result structures.

### `association`

`association` maps one-to-one relationships.

### `collection`

`collection` maps one-to-many relationships.

### Constructor

Constructor-based mapping lets MyBatis instantiate immutable or constructor-initialized objects.

### Discriminator

`discriminator` supports conditional mapping based on column values.

## Automatic Mapping

MyBatis can automatically map columns to object properties when naming can be matched.

## Cache

MyBatis supports both first-level and second-level cache.

### First-Level Cache (Local Cache)

The first-level cache is enabled by default and scoped to a single `SqlSession`.

### Second-Level Cache

The second-level cache is scoped to a mapper namespace and must be explicitly enabled.

#### `mybatis-config.xml`

Enable the cache feature globally in the main configuration.

#### Configure The Mapper File

Add the cache declaration to the corresponding mapper XML file.

#### Make The Entity Class Implement `Serializable`

Serializable entities are commonly required by cache implementations.

#### Configure Cache Strategy

You can control eviction policy, flush interval, size, and read/write behavior.

# Dynamic SQL

## `<if>` Tag

Use `<if>` to conditionally include SQL fragments.

## `<choose>`, `<when>`, And `<otherwise>` Tags

Use them when multiple branches are mutually exclusive.

## `<trim>`, `<where>`, And `<set>` Tags

These tags help clean up leading or trailing SQL keywords and separators.

## `<foreach>` Tag

Use `<foreach>` to iterate over collections, such as building `IN` conditions or batch inserts.

# SQL Builder

## Solution

MyBatis also provides a Java-based SQL builder API for assembling SQL statements programmatically.

# Logging

## Logging Architecture

MyBatis can integrate with several logging frameworks, such as SLF4J, Log4j, JDK logging, and others.

## Add Dependencies

Introduce the required logging dependencies in the project build configuration.

## Enable Configuration

Specify the logging implementation in the MyBatis configuration file when needed.

## Logging Configuration

This project includes logging configuration examples for observing SQL execution and runtime details.

---

For the complete original tutorial details and examples, you can switch back to the Chinese version at any time:

[Read the Chinese version](./README.md)

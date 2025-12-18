# netchat

Современный многопользовательский чат-сервер с защитой от спама и аналитикой.

## Запуск

Для запуска сервера:

```mvn compile exec:java -Dexec.mainClass="server.Main"```

Или скомпилировать и запустить напрямую:

```javac src/main/java/server/Main.java```

```java -cp src/main/java server.Main```

Для подключения клиента используйте telnet:

```telnet localhost 12345```

## Архитектура проекта

```
D:.
|   pom.xml
|   README.md
|
\---src
    \---main
        +---java
        |   +---common
        |   |       ChatMessage.java
        |   |
        |   +---server
        |       AnalyticsBot.java
        |   |       ChatServer.java
        |   |       ClientHandler.java
        |   |       ClientManager.java
        |   |       CommandProcessor.java
        |   |       Logger.java
        |   |       Main.java
        |   |       MessageBroker.java
        |   |       PerformanceMonitor.java
        |   |       ServerConfig.java
        |   |       StatsCalculator.java
        |   |       UserRateLimiter.java
        |   |
        |   \---test
        |           LoadTest.java
        |
        \---resources
            \---server.properties
```

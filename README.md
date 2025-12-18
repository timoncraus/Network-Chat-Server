# netchat

Современный многопользовательский чат-сервер с защитой от спама и аналитикой.

## Запуск

Для запуска сервера выполните следующую команду Maven:

```bash
mvn compile exec:java -Dexec.mainClass="server.Main"
```

Альтернативно, можно собрать проект и запустить его напрямую:

```bash
mvn clean package
java -cp target/classes:target/dependency/* server.Main
```

Также можно скомпилировать и запустить без использования Maven:

```bash
javac -cp src/main/java src/main/java/server/*.java
java -cp src/main/java server.Main
```

Сервер запускается на порту 12345 (настраивается в файле `src/main/resources/server.properties`). После запуска сервер готов к приему клиентских подключений.

Для подключения клиента используйте telnet:

```bash
telnet localhost 12345
```

### Зависимости

Для успешной компиляции и запуска проекта необходимы:
- Java 11 или выше
- Apache Maven 3.6.0 или выше

### Параметры запуска

Сервер использует конфигурационный файл `src/main/resources/server.properties`, в котором можно изменить следующие параметры:
- `server.port` - порт, на котором будет работать сервер (по умолчанию 12345)
- `server.max.clients` - максимальное количество одновременных клиентов (по умолчанию 100)
- `server.client.timeout` - таймаут неактивности клиента в миллисекундах (по умолчанию 300000)
- `server.analytics.enabled` - включение/выключение аналитики (по умолчанию true)

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

Для запуска на Windows:

javac SimpleChatServer.java

java SimpleChatServer

Во втором окне запустить telnet (Для включения telnet необходимо ввести в Пуск "Включение или отключение компонентов Windows" и выбрать "Клиент Telnet"):

telnet localhost 12345

Архитектура проекта:
D:.
|   pom.xml
|   SimpleChatServer.java
|
\---src
    \---main
        +---java
        |   +---common
        |   |       ChatMessage.java
        |   |
        |   +---server
        |   |       AnalyticsBot.java
        |   |       ChatServer.java
        |   |       ClientHandler.java
        |   |       ClientManager.java
        |   |       CommandProcessor.java
        |   |       Main.java
        |   |       MessageBroker.java
        |   |       PerformanceMonitor.java
        |   |       StatsCalculator.java
        |   |
        |   \---test
        |           LoadTest.java
        |
        \---resources

# ToDoList — Spring Boot (без XML и внешнего Tomcat) + JPA-вариант

Этот файл — конспект того же приложения ToDoList, но:

- в виде **Spring Boot** приложения (без `web.xml`, `main.xml` и внешнего Tomcat);
- с двумя вариантами доступа к БД:
  - через **JdbcTemplate**;
  - через **JPA/Hibernate**.

---

## 1. Структура проекта (вариант с Spring Boot + JdbcTemplate)

Отдельный проект, например:

```text
todolist-boot
 ├─ src
 │   ├─ main
 │   │   ├─ java
 │   │   │   └─ org.example
 │   │   │       ├─ TodoListApplication.java
 │   │   │       ├─ controller
 │   │   │       │   └─ UserController.java
 │   │   │       └─ service
 │   │   │           ├─ UserService.java
 │   │   │           └─ interfaces
 │   │   │               └─ IUserService.java
 │   │   └─ resources
 │   │       └─ application.properties
 └─ pom.xml
```

---

## 2. `pom.xml` (Spring Boot + JdbcTemplate)

```xml
<project ...>
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.example</groupId>
    <artifactId>todolist-boot</artifactId>
    <version>1.0.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.0</version>
    </parent>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Web + DispatcherServlet + встроенный Tomcat -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- JDBC + DataSource, JdbcTemplate -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>

        <!-- H2 -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Главное отличие от XML-варианта:**

- нет `web.xml`;
- нет `main.xml`;
- Spring Boot сам:
  - поднимает **DispatcherServlet**;
  - запускает **встроенный Tomcat**;
  - настраивает бины `DataSource` и `JdbcTemplate` на основе `application.properties`.

---

## 3. Точка входа (main-класс)

```java
package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication   // включает @Configuration, @EnableAutoConfiguration, @ComponentScan
public class TodoListApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodoListApplication.class, args);
    }
}
```

### Что делает `@SpringBootApplication`

Это составная аннотация, которая включает:

- **`@ComponentScan`** по пакету `org.example`:
  - находит `@Controller`, `@Service`, `@Repository`, `@Component`;
- **`@EnableAutoConfiguration`**:
  - видит зависимость `spring-boot-starter-web` → поднимает DispatcherServlet и встроенный Tomcat;
  - видит `spring-boot-starter-jdbc` + настройки в `application.properties` → создает `DataSource` и `JdbcTemplate`.

---

## 4. `application.properties` (для JdbcTemplate-варианта)

```properties
spring.datasource.url=jdbc:h2:file:D:/DB_H2/H2/TodoListBootDB
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=Amid
spring.datasource.password=2401

spring.h2.console.enabled=true
spring.h2.console.path=/h2
```

- Настройки `spring.datasource.*` нужны, чтобы Boot мог автоматически поднять:
  - `DataSource`;
  - `JdbcTemplate`, который использует этот `DataSource`.
- Включен web-консоль H2 по URL `/h2`.

**Важно:** теперь **не нужно** описывать `dataSource` и `jdbcTemplate` в XML — Boot создаст их сам.

---

## 5. UserService и UserController (JdbcTemplate внутри Spring Boot)

Логика сервиса и контроллера очень похожа на XML-вариант:

### 5.1. Сервис

```java
package org.example.service;

import org.example.domain.User;
import org.example.service.interfaces.IUserService;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserService implements IUserService {

    private final JdbcTemplate jdbcTemplate;

    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createTableUser() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS Users");
        jdbcTemplate.execute("CREATE TABLE Users(Id LONG, Email VARCHAR(30), Password VARCHAR(30))");
    }

    @Override
    public int createUser(User user) {
        String sql = "INSERT INTO Users (Id, Email, Password) VALUES (?, ?, ?)";
        return jdbcTemplate.update(
                sql,
                user.getId(),
                user.getEmail(),
                user.getPassword()
        );
    }

    @Override
    public User getUser(long id) {
        String query = "SELECT * FROM Users WHERE Id=?";
        return jdbcTemplate.queryForObject(
                query,
                new Object[]{id},
                new BeanPropertyRowMapper<>(User.class)
        );
    }

    @Override
    public int updateUser(User updatedUser, long id) {
        String query =
                "UPDATE Users SET Email='" + updatedUser.getEmail() +
                "', Password='" + updatedUser.getPassword() +
                "' WHERE Id=" + id;
        return jdbcTemplate.update(query);
    }

    @Override
    public int deleteUser(long id) {
        String query = "DELETE FROM Users WHERE Id=" + id;
        return jdbcTemplate.update(query);
    }
}
```

### 5.2. Контроллер

```java
package org.example.controller;

import org.example.domain.User;
import org.example.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 1. Создать таблицу
    @GetMapping(path = "/create_table")
    public ResponseEntity<String> getTest(){
        userService.createTableUser();
        return new ResponseEntity<>("table (re)created", HttpStatus.OK);
    }

    // 2. Создать пользователя
    @PostMapping(path = "/user/create")
    public ResponseEntity<Integer> createUser(@RequestBody User user) {
        int result = userService.createUser(user);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    // 3. Получить пользователя
    @GetMapping("/user/get/{id}")
    public ResponseEntity<User> getUser(@PathVariable("id") long id) {
        User result = userService.getUser(id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    // 4. Обновить пользователя
    @PutMapping(path = "/user/update/{id}")
    public ResponseEntity<Integer> updateUser(@RequestBody User updatedUser,
                                              @PathVariable("id") long id) {
        int result = userService.updateUser(updatedUser, id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    // 5. Удалить пользователя
    @DeleteMapping(path = "/user/delete/{id}")
    public ResponseEntity<Integer> deleteUser(@PathVariable("id") long id) {
        int result = userService.deleteUser(id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
```

### 5.3. URL-ы в Boot-варианте

**Важно:** в Boot по умолчанию **нет контекстного пути по имени war**.  
Поэтому эндпоинты будут:

```text
GET    http://localhost:8080/create_table
POST   http://localhost:8080/user/create
GET    http://localhost:8080/user/get/1
PUT    http://localhost:8080/user/update/1
DELETE http://localhost:8080/user/delete/1
```

Приложение можно запустить:

- `mvn spring-boot:run`, или  
- запустить `TodoListApplication.main()` из IDE.

---

## 6. Что поменялось концептуально (XML vs Boot)

### 6.1. Вариант с XML

- внешний **Tomcat**;
- собираем `war` и деплоим;
- есть `web.xml`;
- есть `main.xml` с:
  - `component-scan`;
  - `mvc:annotation-driven`;
  - ручным объявлением `dataSource` и `jdbcTemplate`.

### 6.2. Вариант с Spring Boot

- встроенный **Tomcat**;
- чаще всего `jar`-приложение;
- **нет** `web.xml` и `main.xml`;
- Boot за счет автоконфигурации:
  - создает DispatcherServlet;
  - запускает встроенный Tomcat;
  - поднимает `DataSource` и `JdbcTemplate` по `application.properties`.

**API и бизнес-логика** практически не меняются — меняется только инфраструктура вокруг.

---

## 7. Та же задача с JPA/Hibernate вместо JdbcTemplate

Теперь тот же ToDoList можно реализовать через **JPA/Hibernate**, чтобы:

- не писать SQL руками;
- работать с сущностями (`User`) как с объектами.

### 7.1. Дополнительная зависимость (Spring Data JPA)

В том же Boot-проекте добавляем в `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

И в `application.properties`:

```properties
spring.jpa.hibernate.ddl-auto=update   # или create / create-drop для учебных проектов
spring.jpa.show-sql=true               # логировать SQL в консоль
spring.jpa.properties.hibernate.format_sql=true
```

Теперь Hibernate:

- строит схему таблиц по аннотациям на сущностях (`@Entity`, `@Table`, `@Column` и т.д.);
- выполняет SQL для `insert/select/update/delete`.

---

### 7.2. Сущность `User` как JPA-entity

```java
package org.example.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "users")   // имя таблицы в БД
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;     // теперь id генерится БД

    @Column(nullable = false, length = 50, unique = true)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    // геттеры/сеттеры и toString()
}
```

Здесь:

- `@Entity` — говорит Hibernate, что это сущность;
- `@Table(name = "users")` — таблица `users`;
- `@Id` + `@GeneratedValue` — первичный ключ, автоинкремент;
- `@Column` — ограничения на колонку.

С учетом `spring.jpa.hibernate.ddl-auto=update` таблица `users` создается автоматически при старте приложения.

---

### 7.3. Репозиторий вместо JdbcTemplate

```java
package org.example.repository;

import org.example.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    // при необходимости можно добавить:
    // Optional<User> findByEmail(String email);
}
```

Spring Data JPA сгенерирует реализацию с методами:

- `save(User user)`
- `findById(Long id)`
- `findAll()`
- `deleteById(Long id)`
- и многими другими.

---

### 7.4. Сервис поверх репозитория

```java
package org.example.service;

import org.example.domain.User;
import org.example.repository.UserRepository;
import org.example.service.interfaces.IUserService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService implements IUserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void createTableUser() {
        // При JPA/Hibernate отдельный метод не нужен:
        // схема создается автоматически (ddl-auto=update/create).
        // Можно оставить пустым, чтобы не ломать контроллер.
    }

    @Override
    public int createUser(User user) {
        User saved = userRepository.save(user);
        // Можно вернуть, например, 1 как "одна запись сохранена"
        return 1;
    }

    @Override
    public User getUser(long id) {
        Optional<User> userOpt = userRepository.findById(id);
        return userOpt.orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Override
    public int updateUser(User updatedUser, long id) {
        User existing = getUser(id);          // бросит исключение, если нет
        existing.setEmail(updatedUser.getEmail());
        existing.setPassword(updatedUser.getPassword());
        userRepository.save(existing);
        return 1;
    }

    @Override
    public int deleteUser(long id) {
        userRepository.deleteById(id);
        return 1;
    }
}
```

Обрати внимание:

- **нет** `JdbcTemplate`;
- **нет** SQL-строк;
- вместо этого — вызовы:
  - `userRepository.save(...)`;
  - `userRepository.findById(...)`;
  - `userRepository.deleteById(...)`.

---

### 7.5. Контроллер с JPA

Контроллер может остаться практически таким же (мы не меняем интерфейс `IUserService`):

```java
package org.example.controller;

import org.example.domain.User;
import org.example.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(path = "/create_table")
    public ResponseEntity<String> getTest(){
        userService.createTableUser(); // при JPA может быть пустым, но эндпоинт не ломаем
        return new ResponseEntity<>("ok", HttpStatus.OK);
    }

    @PostMapping(path = "/user/create")
    public ResponseEntity<Integer> createUser(@RequestBody User user) {
        int result = userService.createUser(user);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    @GetMapping("/user/get/{id}")
    public ResponseEntity<User> getUser(@PathVariable("id") long id) {
        User result = userService.getUser(id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PutMapping(path = "/user/update/{id}")
    public ResponseEntity<Integer> updateUser(@RequestBody User updatedUser,
                                              @PathVariable("id") long id) {
        int result = userService.updateUser(updatedUser, id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @DeleteMapping(path = "/user/delete/{id}")
    public ResponseEntity<Integer> deleteUser(@PathVariable("id") long id) {
        int result = userService.deleteUser(id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
```

С точки зрения клиента (браузер / Postman / `fetch`):

- эндпоинты те же;
- JSON тот же;
- HTTP-коды те же.

Меняется только внутренняя реализация работы с БД.

---

### 7.6. Сравнение JdbcTemplate vs JPA/Hibernate

**JdbcTemplate**

**Плюсы:**

- полный контроль над SQL;
- понятно, какие запросы выполняются;
- легко точечно оптимизировать конкретные запросы.

**Минусы:**

- много повторяющегося кода (SQL, маппинг полей);
- сложнее поддерживать большие модели.

---

**JPA/Hibernate**

**Плюсы:**

- меньше кода, работа с объектами вместо SQL;
- автоматическое создание и обновление схемы;
- мощные возможности:
  - связи `@OneToMany`, `@ManyToOne`, `@ManyToMany`;
  - ленивые загрузки;
  - кэш и т.д.

**Минусы:**

- SQL «под капотом» — иногда приходится разбираться, что именно генерит Hibernate;
- нужно понимать:
  - состояния сущностей;
  - контекст персистентности;
  - кэш первого уровня и т.п.

---

### 7.7. Хороший путь обучения

1. **Сначала JdbcTemplate**  
   Чтобы руками прочувствовать SQL, параметры, маппинг результатов.

2. **Потом JPA/Hibernate**  
   Чтобы понять, как эти вещи автоматизируются на уровне ORM и где это помогает / мешает.

---

Этот файл удобно держать рядом с проектом `todolist-boot` как шпаргалку:

- как выглядит минимальная конфигурация Spring Boot;
- где разница между XML и Boot;
- как перейти с JdbcTemplate на JPA/Hibernate, не трогая API контроллера.

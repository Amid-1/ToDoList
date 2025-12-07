
# ToDoList (Spring MVC + JDBC) — как все работает

## 0. Общая картина

Приложение: простое REST-API для работы с пользователями (`User`), разворачивается на Tomcat.

```text
Браузер / Postman / JS (fetch)
        │
        ▼
HTTP-запрос (URL + метод + заголовки + тело)
        │
        ▼
Tomcat (Servlet Container)
        │
        ▼
Spring DispatcherServlet ("TodoListServlet")
        │
        ▼
UserController (@Controller, @GetMapping/@PostMapping/...)
        │
        ▼
UserService (@Service, бизнес-логика)
        │
        ▼
JdbcTemplate  →  H2 Database (файл D:\DB_H2\H2\TodoListDBH2.mv.db)
        │
        ▲
        └── результат (User/int/строка)
```

---

## 1. Конфигурация Spring (main.xml)

Главный XML (main.xml), который поднимает контекст Spring:

```xml
<!-- main.xml -->
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:context="http://www.springframework.org/schema/context"
       xmlns:mvc="http://www.springframework.org/schema/mvc"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd
           http://www.springframework.org/schema/mvc
           https://www.springframework.org/schema/mvc/spring-mvc.xsd">

    <!-- 1. Сканирование классов с аннотациями @Controller, @Service и т.д. -->
    <context:component-scan base-package="org.example" />

    <!-- 2. Включаем поддержку @GetMapping/@PostMapping/@ResponseBody и т.п. -->
    <mvc:annotation-driven/>

    <!-- 3. DataSource для H2 -->
    <bean id="dataSource"
          class="org.springframework.jdbc.datasource.SingleConnectionDataSource">
        <property name="driverClassName" value="org.h2.Driver"/>
        <property name="url" value="jdbc:h2:file:D:/DB_H2/H2/TodoListDBH2"/>
        <property name="username" value="Amid"/>
        <property name="password" value="2401"/>
    </bean>

    <!-- 4. JdbcTemplate, работающий поверх dataSource -->
    <bean id="jdbcTemplate" class="org.springframework.jdbc.core.JdbcTemplate">
        <property name="dataSource" ref="dataSource"/>
    </bean>
</beans>
```


```xml
<!-- web.xml -->
<?xml version="1.0" encoding="UTF-8"?>

<web-app>
    <display-name>ToDoList</display-name>
    <servlet>
        <servlet-name>TodoListServlet</servlet-name>
        <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
        <init-param>
            <param-name>contextConfigLocation</param-name>
            <param-value>/WEB-INF/main.xml</param-value>
        </init-param>
    </servlet>

    <servlet-mapping>
        <servlet-name>TodoListServlet</servlet-name>
        <url-pattern>/</url-pattern>
    </servlet-mapping>
</web-app>

```

### Что тут происходит

1. **`component-scan`**: Spring на старте пробегает по пакету `org.example` и ищет классы с аннотациями  
   `@Controller`, `@Service`, `@Repository`, `@Component`. Для каждого такого класса создается **бин**.

2. **`mvc:annotation-driven`**: включает Spring MVC магию:
   - маппинг `@GetMapping/@PostMapping/...`;
   - конвертация JSON ⇄ Java (Jackson);
   - `@RequestBody`, `@ResponseBody` и т.п.

3. **`dataSource`**: бин с настройкой подключения к H2.

4. **`jdbcTemplate`**: бин, который знает, как выполнять SQL против `dataSource`.

---

## 2. Как создаются и связываются бины

### UserService

```java
@Service
public class UserService implements IUserService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // методы createTableUser, createUser, getUser, updateUser, deleteUser ...
}
```

- `@Service` → Spring создаёт бин `userService`.
- В конструктор нужно передать `JdbcTemplate`.
- В контексте уже есть бин `jdbcTemplate` из `main.xml`.
- Spring видит `@Autowired` и **автоматически подставляет** существующий бин `JdbcTemplate`.

### UserController

```java
@Controller
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // методы-обработчики запросов
}
```

- `@Controller` → бин контроллера.
- В конструкторе — `UserService`, который уже есть в контексте.
- Опять же, `@Autowired` → Spring связывает бины.

**Итог:** цепочка бинов:

```text
dataSource  →  jdbcTemplate  →  userService  →  userController
(из XML)       (из XML)         (@Service)      (@Controller)
```

---

## 3. HTTP API: эндпоинты контроллера

```java
@Controller
public class UserController {

    // 1. Создать таблицу
    @GetMapping(path = "/create_table")
    public ResponseEntity<String> getTest(){
        userService.createTableUser();
        return new ResponseEntity<>("this is our test massage", HttpStatus.OK);
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

### Что здесь важно

- **Эндпоинт** — это «входная точка» API: комбинация HTTP-метода + URL.
- У тебя есть:

| Действие              | Метод | URL                  | Описание               |
|-----------------------|-------|----------------------|------------------------|
| Создать таблицу       | GET   | `/create_table`      | Создает таблицу Users  |
| Создать пользователя  | POST  | `/user/create`       | Вставка записи         |
| Получить пользователя | GET   | `/user/get/{id}`     | Чтение по id           |
| Обновить пользователя | PUT   | `/user/update/{id}`  | Обновление             |
| Удалить пользователя  | DELETE| `/user/delete/{id}`  | Удаление               |

- `@PathVariable("id")` — берет `id` из куска URL `/user/get/1`, `/user/update/1` и т.д.
- `@RequestBody User user` — говорит Spring: «тело запроса в JSON нужно распарсить в объект `User`».

---

## 4. Сервисный слой и работа с БД

```java
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
            "', password='" + updatedUser.getPassword() +
            "' WHERE id=" + id;
    return jdbcTemplate.update(query);
}

@Override
public int deleteUser(long id) {
    String query = "DELETE FROM Users WHERE Id=" + id;
    return jdbcTemplate.update(query);
}
```

### Кратко

- `jdbcTemplate.update(...)` — для `INSERT/UPDATE/DELETE`, возвращает количество затронутых строк.
- `jdbcTemplate.queryForObject(...)` — для `SELECT`, возвращает один объект.
- `BeanPropertyRowMapper<>(User.class)` — автоматически маппит колонки (`Id`, `Email`, `Password`) в поля класса `User` по именам.

> На будущее: в `updateUser` и `deleteUser` лучше тоже использовать `?`-параметры, чтобы не было SQL Injection. Для учебного примера так тоже можно.

---

## 5. Жизненный цикл HTTP-запроса

Разберем, что происходит, когда ты делаешь, например:

```http
POST /ToDoList_war_exploded/user/create HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "id": 1,
  "email": "qwerty@gmail.com",
  "password": "qwerty123"
}
```

### Шаги

1. **Браузер / Postman / JS fetch** формирует HTTP-запрос.
2. **Tomcat** принимает запрос на `http://localhost:8080`.
3. По контекстному пути `/ToDoList_war_exploded` Tomcat находит твое приложение.
4. Запрос передается в `DispatcherServlet` (у тебя — `TodoListServlet`).
5. `DispatcherServlet`:
   - смотрит метод (`POST`) и URL (`/user/create`);
   - находит метод контроллера с `@PostMapping("/user/create")`.
6. Для методов с `@RequestBody`:
   - читает тело запроса;
   - по `Content-Type: application/json` понимает, что это JSON;
   - Jackson парсит JSON в `User`.
7. Вызывает метод контроллера:

   ```java
   createUser(User user);
   ```

8. Контроллер вызывает сервис:

   ```java
   userService.createUser(user);
   ```

9. `UserService` через `JdbcTemplate` сохраняет данные в БД H2.
10. Сервис возвращает `int` (количество строк), контроллер упаковывает это в `ResponseEntity` с кодом **201**:

    ```java
    return new ResponseEntity<>(result, HttpStatus.CREATED);
    ```

11. **Ответ** уходит в браузер.

---

## 6. Что такое fetch и как мы его используем

Ты вызывал API прямо из консоли браузера.

### 1) Создать таблицу

```js
fetch("http://localhost:8080/ToDoList_war_exploded/create_table", {
  method: "GET"
}).then(r => r.text()).then(console.log);
```

### 2) Создать пользователя

```js
fetch("http://localhost:8080/ToDoList_war_exploded/user/create", {
  method: "POST",
  headers: {
    "Content-Type": "application/json;charset=UTF-8"
  },
  body: JSON.stringify({
    id: 1,
    email: "qwerty@gmail.com",
    password: "qwerty123"
  })
}).then(r => r.text()).then(console.log);
```

### 3) Получить пользователя

```js
fetch("http://localhost:8080/ToDoList_war_exploded/user/get/1", {
  method: "GET",
  headers: {
    "Accept": "application/json"
  }
}).then(r => r.json()).then(console.log);
```

### 4) Обновить пользователя

```js
fetch("http://localhost:8080/ToDoList_war_exploded/user/update/1", {
  method: "PUT",
  headers: {
    "Content-Type": "application/json;charset=UTF-8"
  },
  body: JSON.stringify({
    id: 1,
    email: "qwerty@gmail.com",
    password: "qwerty456"
  })
}).then(r => r.text()).then(console.log);
```

### 5) Удалить пользователя

```js
fetch("http://localhost:8080/ToDoList_war_exploded/user/delete/1", {
  method: "DELETE"
}).then(r => r.text()).then(console.log);
```

**Важно:** URL + HTTP-метод должны совпадать с контроллером:

- `PUT /user/update/1` → `@PutMapping("/user/update/{id}")`
- `DELETE /user/delete/1` → `@DeleteMapping("/user/delete/{id}")`

Когда отправляешь `DELETE` на `/user/update/1`, сервер логично отдаст **405 Method Not Allowed**.

---

## 7. Аннотации Spring MVC — краткий справочник

- `@Controller` — класс-контроллер, обрабатывает web-запросы.
- `@Service` — сервисный бин, бизнес-логика.
- `@Autowired` — автоматическая подстановка бина в конструктор/поле/сеттер.
- `@GetMapping("/path")` — метод контроллера для HTTP GET.
- `@PostMapping("/path")` — для HTTP POST.
- `@PutMapping("/path")` — для HTTP PUT.
- `@DeleteMapping("/path")` — для HTTP DELETE.
- `@RequestMapping` — «универсальная» аннотация, может задавать и путь, и методы.
- `@PathVariable("id")` — взять значение `{id}` из URL.
- `@RequestBody` — прочитать тело запроса (обычно JSON) и сконвертировать в объект.
- `ResponseEntity<T>` — обертка для ответа: тело + статус + заголовки.

---

## 8. Частые ошибки, которые мы встретили

1. **404 Not Found**
   - URL не совпал ни с одним эндпоинтом.
   - Например, метод ожидает `/user/create`, а запрос уходит на `/users/create`.

2. **405 Method Not Allowed**
   - Путь есть, но HTTP-метод другой.
   - Например, `DELETE /user/update/1` при `@PutMapping("/user/update/{id}")`.

3. **500 Internal Server Error**
   - Любое необработанное исключение внутри приложения.
   - Примеры:
     - **H2**: база уже используется другим процессом.
     - `IllegalArgumentException: Name for argument of type [long] not specified`  
       → решается указанием `@PathVariable("id")`.

4. **Блокировка H2 Database**
   - Одновременный доступ из двух приложений/IDE к одному файлу БД.
   - Решения: закрыть лишние приложения, использовать серверный режим H2 или разные файлы.

---

## 9. DevTools: Network и Console

- **Console**:
  - сюда вводишь `fetch(...)`;
  - видишь ошибки JS (например, опечатка `etch` вместо `fetch`).

- **Network**:
  - показывает каждый запрос:
    - **Headers** — метод, URL, статус, заголовки, куки;
    - **Payload / Request body** — что реально ушло на сервер;
    - **Response** — что вернул сервер;
    - **Cookies** — например, `JSESSIONID`.

Это основной инструмент, чтобы понимать, что реально отправляется и что отвечает приложение.

---

## 10. Итоговая картина словами

1. **Tomcat** поднимает твое приложение и `DispatcherServlet`.
2. `main.xml` создает бины `dataSource`, `jdbcTemplate` и включает сканирование компонентов.
3. Spring находит `UserService` и `UserController`, создает их и связывает через `@Autowired`.
4. Любой HTTP-запрос к `http://localhost:8080/ToDoList_war_exploded/...` приходит в `DispatcherServlet`.
5. `DispatcherServlet` находит нужный метод контроллера по **методу** (GET/POST/PUT/DELETE) и **пути** (`/user/get/{id}`, `/user/create` и т.п.).
6. Spring собирает аргументы метода (`@PathVariable`, `@RequestBody`) и вызывает его.
7. Контроллер обращается к `UserService`, который через `JdbcTemplate` работает с БД H2.
8. Результат упаковывается в `ResponseEntity` и уходит клиенту.
9. В браузере можно:
   - вызывать эндпоинты через `fetch(...)` в консоли;
   - проверять работу и ошибки через вкладку **Network**.

Кратко:

> **URL + HTTP-метод → контроллер → сервис → БД → обратно в Response.**

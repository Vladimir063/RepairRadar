# RepairRadar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Создать RepairRadar на Java 21 с PostgreSQL, потоковым импортом 100 случайных адресов ГАР, атомарным добавлением новых данных и Postman-коллекцией.

**Architecture:** Spring MVC вызывает сервис импорта; потоковый StAX-reader формирует каталог подходящих домов до начала транзакции. Отдельный transactional bean под блокировкой исключает сохранённые GUID, выбирает до 100 новых адресов и добавляет их через Spring Data JPA; PostgreSQL создаёт схему собственным init SQL при первом старте контейнера. Полная административная цепочка, дом и дополнительные номера собираются в `full_address`.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA/Hibernate, PostgreSQL 18.6, Maven Wrapper, JUnit, Mockito, Testcontainers, Docker Compose, Postman v2.1.

**Spec:** `docs/superpowers/specs/2026-09-21-repairradar.md`.

## Global Constraints

- «Приложение RepairRadar на Java 21, стабильном Spring Boot, Maven и Spring Data JPA.»
- «Названия столбцов английские.»
- «Первичный ключ — `house_guid`, PostgreSQL UUID, значение `Дом_OBJECTGUID`; искусственного идентификатора нет.»
- «Каждый успешный импорт добавляет до 100 новых адресов, сохраняя существующие строки без изменений.»
- «При ошибке импорта прежние данные сохраняются.»
- «Нужна Postman-коллекция для обоих запросов.»
- Рабочий каталог Windows: `D:\RepairRadar`; все текстовые файлы UTF-8.
- Только стабильные фиксированные версии; Java target/release ровно 21.
- Таблица готова после инициализации PostgreSQL, до запуска Spring; Hibernate `ddl-auto=validate`.
- XML/CSV не менять и не копировать в ресурсы приложения. Не запускать старые экспортёры как часть импорта.
- В этой задаче создаются только документы плана и спецификации. Реализация — следующий согласованный этап.

## Review Focus

1. XML иерархии 2,45 ГБ: потоковое чтение без DOM/readAllBytes и без накопления всех ITEM; проверка heap на реальных файлах — Task 2/5.
2. Отсутствующие предки, неактивные дома и повторные GUID: не создавать усечённый адрес и не смещать выборку дублями — Task 2.
3. Номера с буквами, дробями, двумя дополнительными частями и неизвестными кодами типов: сохранять значения и не выдумывать подписи — Task 2.
4. Ошибка в середине вставки либо параллельные POST/DELETE: rollback и сериализация изменений — Task 3/4.
5. Существующий Docker volume: init SQL не выполняется повторно; рестарт сохраняет данные, приложение проверяет схему — Task 1/5.

---

## Проверенные исходные данные и источники

- В проекте ещё нет `pom.xml`, Spring-приложения и Compose. Локального `.git` при осмотре нет: коммиты в шагах выполняются только при наличии репозитория, автоматически `git init` не делать.
- `export_xml_addresses.py` описывает прежнюю выборку CSV. Он используется для понимания фильтров, но не как runtime-зависимость.
- `AS_ADDR_OBJ` около 8,7 МБ; `AS_HOUSES` около 173 МБ; `AS_ADM_HIERARCHY` около 2,45 ГБ. Остальные XML для этой задачи не читать.
- Фактические элементы: `ADDRESSOBJECTS/OBJECT`, `HOUSES/HOUSE`, `ITEMS/ITEM`. В иерархии есть `ISACTIVE`, `PATH`, `REGIONCODE`; фильтр `ISACTUAL` применяется только к объектам и домам.
- Пример из CSV: house GUID `35e82bea-9430-4e7a-85f2-0127695cea3d`, house OBJECTID `62540000`, улица `ул. Верхняя`, дом `3`, ADDNUM1 `2`, ADDTYPE1 `1`, HOUSETYPE `2`.
- [Spring Boot](https://spring.io/projects/spring-boot), [совместимость Java](https://docs.spring.io/spring-boot/system-requirements.html), [релизы PostgreSQL](https://www.postgresql.org/docs/release/), [Docker PostgreSQL guide](https://docs.docker.com/guides/postgresql/). Проверено при планировании: Java 21 поддерживается, для PostgreSQL 18 том монтируется в `/var/lib/postgresql`.
- `java`, `mvn`, `docker`, `git` найдены в PATH; версии и работа Docker Engine ещё не проверялись.
- Навыки исполнения `superpowers:executing-plans` и `superpowers:subagent-driven-development` не обнаружены среди доступных навыков. Перед исполнением по ним проверить доступность; если по-прежнему отсутствуют, сообщить об этом и согласовать обычное исполнение без этих навыков. Этот документ не утверждает, что они установлены.

## Карта файлов и интерфейсов

Все Java-файлы лежат в `src/main/java/ru/repairradar/`, тесты — в `src/test/java/ru/repairradar/`.

| Файл | Ответственность |
|---|---|
| `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | Сборка и Maven Wrapper |
| `.gitignore`, `.env.example`, `README.md` | Исключения, локальная конфигурация, запуск |
| `compose.yaml`, `docker/postgres/init/001-addresses.sql` | PostgreSQL и единственный источник DDL |
| `src/main/resources/application.yaml` | Datasource, JPA, путь к XML |
| `RepairRadarApplication.java` | Точка входа |
| `address/Address.java`, `address/AddressRepository.java` | JPA-сущность и Spring Data repository |
| `address/AddressStore.java` | Транзакции append/clear и блокировка таблицы |
| `address/AddressImportService.java` | Оркестрация чтения/добавления и защита от конкурирующих операций |
| `address/AddressController.java`, `address/ImportResult.java` | HTTP и DTO ответа |
| `address/AddressExceptionHandler.java` | ProblemDetail для ошибок |
| `gar/GarFiles.java`, `gar/GarXmlReader.java` | Выбор ровно трёх файлов и StAX |
| `gar/GarAddressReader.java`, `gar/ReservoirSampler.java` | Соединение записей и равномерная выборка |
| `gar/AddressFormatter.java` | Полный адрес и подписи типов |
| `gar/AddressRow.java`, `gar/GarObject.java`, `gar/GarHouse.java` | Компактные immutable record-модели |
| `gar/GarImportException.java` | Ошибка источников с машинным кодом |
| `address/OperationInProgressException.java` | Конфликт параллельных запросов |
| `postman/RepairRadar.postman_collection.json` | Два запроса и assertions |
| `gar/GarFixture.java` (test) | Генератор маленьких самостоятельных XML |
| `gar/GarFilesTest.java`, `gar/GarXmlReaderTest.java`, `gar/GarAddressReaderTest.java`, `gar/AddressFormatterTest.java`, `gar/ReservoirSamplerTest.java` (test) | Парсинг, форматирование, выборка |
| `address/AddressStoreIT.java`, `address/AddressApiIT.java` (test) | Реальная PostgreSQL, транзакции, HTTP |
| `address/AddressImportServiceTest.java` (test) | Ошибки до записи и одновременные операции |

## Task 1: Запускаемые PostgreSQL и Spring Boot с точной схемой

**Files:** создать `pom.xml`, Wrapper, `.gitignore`, `.env.example`, `compose.yaml`, `docker/postgres/init/001-addresses.sql`, `application.yaml`, `RepairRadarApplication.java`, `address/Address.java`, `address/AddressRepository.java`; начать `README.md` и `address/AddressStoreIT.java`.

**Interfaces:** `AddressRepository extends JpaRepository<Address, UUID>`; `Address` отображает таблицу `addresses`, `getHouseGuid()` возвращает UUID. Конструктор без аргументов `protected`; импорт использует отдельный native insert вместо save(). Идентификатор задаётся из ГАР, без `@GeneratedValue`.

- [ ] **1. Проверить окружение и стабильные версии.**

```powershell
java -version
mvn -version
docker version
docker compose version
```

Проверить доступность parent `org.springframework.boot:spring-boot-starter-parent:4.1.1` и образа `postgres:18.6` до фиксации. Не подменять недоступный релиз на preview. Maven Wrapper сгенерировать официальным wrapper plugin, закрепить стабильный Maven 3.9.x и точный URL в properties; выбрать доступный patch после проверки официального Maven release index. В README записать фактические версии.

- [ ] **2. Создать DDL без дополнительных столбцов.**

```sql
CREATE TABLE IF NOT EXISTS public.addresses (
    city text NOT NULL,
    full_address text NOT NULL,
    street text NOT NULL,
    house_number text NOT NULL,
    additional_number_1 text,
    additional_type_1 integer,
    additional_number_2 text,
    additional_type_2 integer,
    house_type integer,
    street_object_id bigint NOT NULL,
    street_guid uuid NOT NULL,
    house_object_id bigint NOT NULL,
    house_guid uuid PRIMARY KEY,
    CONSTRAINT addresses_nonempty CHECK (
        btrim(city) <> '' AND btrim(full_address) <> ''
        AND btrim(street) <> '' AND btrim(house_number) <> ''
    )
);
```

`additional_type_1/2` и `house_type` сохраняют исходные цифровые коды. Номера хранятся текстом. `OBJECTID` — bigint/Long; GUID — uuid/UUID. Пустые необязательные атрибуты преобразуются в null. Соответствие исходным полям в README: `Город→city`, `Адресная_иерархия→full_address`, `Улица→street`, `Дом→house_number`, `Доп_номер_1/2→additional_number_1/2`, `Тип_доп_номера_1/2→additional_type_1/2`, `Тип_дома→house_type`, `Улица_OBJECTID→street_object_id`, `Улица_OBJECTGUID→street_guid`, `Дом_OBJECTID→house_object_id`, `Дом_OBJECTGUID→house_guid`.

- [ ] **3. Создать Compose и конфигурацию приложения.**

```yaml
name: repairradar
services:
  postgres:
    image: postgres:18.6
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-repairradar}
      POSTGRES_USER: ${POSTGRES_USER:-repairradar}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-repairradar_local}
    ports:
      - "127.0.0.1:${POSTGRES_PORT:-5432}:5432"
    volumes:
      - postgres_data:/var/lib/postgresql
      - ./docker/postgres/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
      interval: 5s
      timeout: 5s
      retries: 20
volumes:
  postgres_data:
```

`application.yaml`:

```yaml
spring:
  application:
    name: RepairRadar
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/repairradar}
    username: ${DB_USERNAME:repairradar}
    password: ${DB_PASSWORD:repairradar_local}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.batch_size: 100
repairradar:
  gar:
    directory: ${GAR_DIRECTORY:./77}
server:
  port: ${SERVER_PORT:8080}
```

`.env.example` содержит `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` с этими defaults. README явно объясняет: `.env` читает Compose; Java использует process env `DB_URL/DB_USERNAME/DB_PASSWORD/GAR_DIRECTORY`. `.gitignore`: `/target/`, `/.env`, `/77/`, `/.idea/`, `*.iml`; Wrapper не исключать.

- [ ] **4. Создать Maven skeleton и mapping.**

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.1.1</version>
  <relativePath/>
</parent>
<groupId>ru.repairradar</groupId>
<artifactId>repairradar</artifactId>
<version>0.0.1-SNAPSHOT</version>
<name>RepairRadar</name>
<properties>
  <java.version>21</java.version>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```

SNAPSHOT здесь — версия собственного приложения, не зависимости. Зависимости: Boot `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `org.postgresql:postgresql` runtime; test — `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`, Testcontainers PostgreSQL и JUnit Jupiter. Координаты модулей Testcontainers выбрать по управляемой версии Boot BOM (для 2.x — `testcontainers-postgresql`/`testcontainers-junit-jupiter`), версии вручную не смешивать. Добавить Boot Maven plugin и Failsafe `integration-test`, `verify`; `*Test` выполняет Surefire, `*IT` — Failsafe. DDL включить в test resources из `docker/postgres/init`, не дублировать файл.

```java
@SpringBootApplication
public class RepairRadarApplication {
    public static void main(String[] args) {
        SpringApplication.run(RepairRadarApplication.class, args);
    }
}

// Address.java: для каждого поля использовать явное @Column(name=...).
@Id
@Column(name = "house_guid", nullable = false)
private UUID houseGuid;

// Для всех text-полей columnDefinition = "text".
public interface AddressRepository extends JpaRepository<Address, UUID> {}
```

- [ ] **5. Проверить схему до запуска приложения и старт контекста.**

```powershell
docker compose up -d --wait
docker compose exec -T postgres psql -U repairradar -d repairradar -c '\d public.addresses'
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Начать `AddressStoreIT` с Testcontainers PostgreSQL 18.6, `.withInitScript("001-addresses.sql")`, `@DynamicPropertySource` и `@SpringBootTest`. Проверка:

```java
@Test
void schemaHasExactlyThirteenColumnsAndUuidPrimaryKey() {
    assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
        + "where table_schema='public' and table_name='addresses'", Integer.class)).isEqualTo(13);
    assertThat(jdbc.queryForObject("select data_type from information_schema.columns "
        + "where table_schema='public' and table_name='addresses' and column_name='house_guid'",
        String.class)).isEqualTo("uuid");
    assertThat(jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint "
        + "where conrelid='public.addresses'::regclass and contype='p'", String.class))
        .isEqualTo("PRIMARY KEY (house_guid)");
}
```

Отдельная test-база, не Compose-база. В интеграционных тестах Docker обязателен: не выдавать пропуск за успешную проверку.

- [ ] **6. Если есть Git, зафиксировать перечисленные файлы:** `git commit -m "feat: bootstrap RepairRadar and PostgreSQL schema"` после адресного `git add` файлов Task 1.

## Task 2: Потоковое чтение ГАР, каталог кандидатов и случайная выборка

**Files:** создать все файлы пакета `gar` из карты и их unit-тесты.

**Interfaces:**

```java
public record AddressRow(String city, String fullAddress, String street,
    String houseNumber, String additionalNumber1, Integer additionalType1,
    String additionalNumber2, Integer additionalType2, Integer houseType,
    long streetObjectId, UUID streetGuid, long houseObjectId, UUID houseGuid) {}
public record GarObject(long objectId, UUID guid, String name, String typeName, int level) {}
public record GarHouse(long objectId, UUID guid, String number, Integer type,
    String additionalNumber1, Integer additionalType1,
    String additionalNumber2, Integer additionalType2) {}
public record GarFiles(Path objects, Path houses, Path hierarchy) {
    public static GarFiles discover(Path directory); // реализация ниже
}
// GarXmlReader: void read(Path file, String element, Consumer<Map<String,String>> consumer)
// AddressFormatter: String format(List<GarObject> ancestors, GarHouse house)
// ReservoirSampler<T>: constructor(int capacity, RandomGenerator random),
//                     void accept(T value), List<T> values()
// GarAddressReader: List<AddressRow> readCandidates(Path directory)
// GarImportException extends RuntimeException: constructor(String code, String message, Throwable cause),
//                                            constructor(String code, String message), String code()
```

Сигнатуры в блоке — контракт, не готовый компилируемый файл. Reader возвращает уникальных подходящих кандидатов. ReservoirSampler применяется в AddressStore после исключения сохранённых GUID, с capacity 100.

- [ ] **1. Создать генератор XML и failing happy-path test.** `GarFixture.write(Path directory, int count)` создаёт три файла с именами `AS_ADDR_OBJ_20260917_fixture.XML`, `AS_HOUSES_20260917_fixture.XML`, `AS_ADM_HIERARCHY_20260917_fixture.XML`. Содержимое объектов:

```xml
<ADDRESSOBJECTS>
 <OBJECT OBJECTID="1" OBJECTGUID="00000000-0000-0000-0000-000000000001" NAME="Москва" TYPENAME="г." LEVEL="1" ISACTIVE="1" ISACTUAL="1"/>
 <OBJECT OBJECTID="2" OBJECTGUID="00000000-0000-0000-0000-000000000002" NAME="Верхняя" TYPENAME="ул." LEVEL="8" ISACTIVE="1" ISACTUAL="1"/>
</ADDRESSOBJECTS>
```

Для `i=0..count-1`, `id=1000+i`, GUID `new UUID(0, id)`, записать HOUSE и ITEM в корнях `HOUSES`/`ITEMS`:

```java
String house = "<HOUSE OBJECTID=\"%d\" OBJECTGUID=\"%s\" HOUSENUM=\"%d\" HOUSETYPE=\"2\" "
    + "ADDNUM1=\"2\" ADDTYPE1=\"1\" ISACTIVE=\"1\" ISACTUAL=\"1\"/>";
String item = "<ITEM OBJECTID=\"%d\" REGIONCODE=\"77\" ISACTIVE=\"1\" PATH=\"1.2.%d\"/>";
// house.formatted(id, new UUID(0, id), i + 1); item.formatted(id, id)
```

Генератор использует `Files.writeString(..., UTF_8)`; возвращает `GarFiles.discover(directory)`. В тесте использовать `@TempDir Path directory`:

```java
@Test
void readsDistinctCandidates() {
    GarFixture.write(directory, 150);
    var rows = new GarAddressReader().readCandidates(directory);
    assertThat(rows).hasSize(150);
    assertThat(rows).extracting(AddressRow::houseGuid).doesNotHaveDuplicates();
    assertThat(rows).allSatisfy(row -> {
        assertThat(row.fullAddress()).startsWith("г. Москва, ул. Верхняя, д. ");
        assertThat(row.fullAddress()).endsWith(", корп. 2");
        assertThat(row.city()).isEqualTo("Москва");
    });
}
```

Запуск: `.\mvnw.cmd -Dtest=GarAddressReaderTest test`; сначала ожидается отсутствие реализации.

- [ ] **2. Реализовать обнаружение файлов и StAX reader.** Имена проверять regex `^AS_ADDR_OBJ_(\d{8})_.+\.XML$`, аналогично HOUSES и ADM_HIERARCHY, case-insensitive. `Files.list` закрывать. Ноль/два совпадения или разные даты — `GarImportException("GAR_SOURCE_INVALID", ...)`. Проверить readable regular file. Не выбирать «первый попавшийся» файл и не захватывать PARAMS/DIVISION.

```java
XMLInputFactory factory = XMLInputFactory.newFactory();
factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
    throw new XMLStreamException("External entities are disabled");
});
try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
    XMLStreamReader reader = factory.createXMLStreamReader(input);
    try {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.DTD) throw new XMLStreamException("DTD is disabled");
            if (event != XMLStreamConstants.START_ELEMENT || !element.equals(reader.getLocalName())) continue;
            Map<String, String> attributes = new HashMap<>();
            for (int i = 0; i < reader.getAttributeCount(); i++)
                attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
            consumer.accept(attributes);
        }
    } finally { reader.close(); }
} catch (IOException | XMLStreamException e) {
    throw new GarImportException("GAR_XML_INVALID", "Cannot read " + file.getFileName(), e);
}
```

Проверять ожидаемый корневой элемент каждого файла (ADDRESSOBJECTS, HOUSES, ITEMS); неправильный корень — GAR_XML_INVALID, а корректный пустой документ — пустой каталог. У подходящего AddressRow обязательны ненулевые UUID и непустые после strip city/fullAddress/street/houseNumber; невалидные кандидаты исключать со счётчиком причин до записи. Не сохранять Map для всех hierarchy ITEM. Не держать reader между запросами. Ошибки обязательных чисел/UUID включают имя файла и OBJECTID в серверном логе.

- [ ] **3. Реализовать форматирование с сохранением обеих дополнительных частей.** Подписи основных типов: `2→д.`; дополнительных: `1→корп.`, `2→стр.`. При наличии иных кодов не угадывать: основной `тип дома <код>`, дополнительный `тип доп. номера <код>`. При отсутствии типа с существующим номером: `дом` / `доп. номер`. Это явное правило для отсутствующих в папке справочников, а не молчаливое отбрасывание данных. Номер отсутствует — часть не добавлять. Все исходные коды также сохраняются в столбцах.

```java
List<String> parts = new ArrayList<>();
for (GarObject ancestor : ancestors)
    parts.add(ancestor.typeName().strip() + " " + ancestor.name().strip());
String label = house.type() == null ? "дом" : house.type() == 2 ? "д." : "тип дома " + house.type();
parts.add(label + " " + house.number().strip());
// Обе пары обрабатываются одинаково и строго по порядку ADDNUM1, ADDNUM2:
String[] numbers = {house.additionalNumber1(), house.additionalNumber2()};
Integer[] types = {house.additionalType1(), house.additionalType2()};
for (int i = 0; i < numbers.length; i++) {
    if (numbers[i] == null || numbers[i].isBlank()) continue;
    String extra = types[i] == null ? "доп. номер" : switch (types[i]) {
        case 1 -> "корп.";
        case 2 -> "стр.";
        default -> "тип доп. номера " + types[i];
    };
    parts.add(extra + " " + numbers[i].strip());
}
return String.join(", ", parts);
```

Не приписывать «г. Москва» повторно: она уже есть в цепочке. TYPENAME и NAME брать из XML, без ручного изменения сокращений улиц.

```java
@Test
void retainsTwoExtraNumbersAndUnknownType() {
    var ancestors = List.of(new GarObject(1, new UUID(0,1), "Москва", "г.", 1));
    var house = new GarHouse(1000, new UUID(0,1000), "12/3А", 2, "2Б", 1, "7", 99);
    assertThat(new AddressFormatter().format(ancestors, house))
        .isEqualTo("г. Москва, д. 12/3А, корп. 2Б, тип доп. номера 99 7");
}
```

- [ ] **4. Реализовать join и отдельный ReservoirSampler.** Загружать только compact `GarObject` для активных актуальных OBJECT и `GarHouse` для активных актуальных HOUSE с непустым HOUSENUM. Повторную активную запись с тем же OBJECTID пропускать, сохраняя первую; различающиеся значения логировать с OBJECTID. Дубли объектов/домов сами по себе не прерывают импорт. Для каждого активного ITEM региона 77, чей OBJECTID есть в houses:
  - PATH разбить по точкам, последняя часть должна совпадать с домом.
  - Все предки до дома должны присутствовать в active objects; цепочку с пропусками, повторными OBJECTID или без московского корня/улицы уровня 8 исключить и посчитать в логе.
  - Последний предок с LEVEL=8 определяет street. Сохранить всю валидную цепочку в full_address.
  - Дедупликация кандидатов по UUID дома до увеличения счётчика eligible, не только по OBJECTID. Повторный одинаковый ITEM не меняет вес дома.
  - Сохранить один AddressRow на уникальный GUID в каталоге кандидатов. Менее 100 кандидатов и пустой корректный набор допустимы. Повторный GUID пропускается: первый валидный кандидат определяет значения.

```java
// Поля ReservoirSampler<T>: int capacity; RandomGenerator random;
// List<T> selected = new ArrayList<>(); long seen;
public void accept(T value) {
    seen++;
    if (selected.size() < capacity) selected.add(value);
    else {
        long index = random.nextLong(seen);
        if (index < capacity) selected.set((int) index, value);
    }
}
public List<T> values() { return List.copyOf(selected); }
```

Для production `RandomGenerator.getDefault()` передаётся в AddressStore на каждый импорт, без фиксированного seed. Память O(активные объекты + активные дома + подходящие кандидаты + сохранённые GUID); не называть её O(100). Все hierarchy ITEM не накапливать: хранить только валидные дома, а не квартиры/комнаты/историю. Каталог кандидатов нужен для добора без повторного чтения XML после получения блокировки. Временные индексы освобождаются после чтения; память измеряется в Task 5. Логировать начало/конец фаз и счётчики, а не каждую строку.

- [ ] **5. Добавить негативные тесты и проверки выборки.**

```java
@Test
void acceptsFewerThanOneHundredCandidates() {
    GarFixture.write(directory, 99);
    assertThat(new GarAddressReader().readCandidates(directory)).hasSize(99);
}
@Test
void reservoirCanReplaceEarlyElements() {
    RandomGenerator random = mock(RandomGenerator.class);
    when(random.nextLong(3)).thenReturn(0L);
    var sampler = new ReservoirSampler<Integer>(2, random);
    sampler.accept(10); sampler.accept(20); sampler.accept(30);
    assertThat(sampler.values()).containsExactly(30, 20);
}
```

В `GarFixture` тесты изменяют конкретные строки через `Files.readString/writeString` только маленьких fixtures. Параметризовать замены в XML:

```java
@ParameterizedTest
@CsvSource({"ISACTIVE,0", "ISACTUAL,0"})
void excludesInactiveHouses(String attribute, String value) throws Exception {
    GarFiles files = GarFixture.write(directory, 101);
    String xml = Files.readString(files.houses());
    Files.writeString(files.houses(), xml.replaceFirst(attribute + "=\"1\"", attribute + "=\"" + value + "\""));
    var rows = new GarAddressReader().readCandidates(directory);
    assertThat(rows).extracting(AddressRow::houseObjectId).doesNotContain(1000L);
}
```

Отдельные случаи с явными assertions: удалить OBJECT улицы → пустой список кандидатов; заменить PATH первого дома `1.2.1000` на `1.999.2.1000` → дом 1000 исключён; добавить копию первого ITEM при 99 домах → ровно 99 кандидатов; повторить GUID у двух домов → нет двух строк с этим GUID; обрезать закрывающий тег → `GAR_XML_INVALID`; вставить DOCTYPE external entity → ошибка и resolver не читает ресурс; дублировать файл HOUSES → `GAR_SOURCE_INVALID`; сменить дату одного файла → `GAR_SOURCE_INVALID`. Проверить отсутствие ADDNUM2 → null в поле и отсутствие лишней запятой. Два ReservoirSampler с capacity 100 и `new Random(42)` на одинаковом потоке дают одинаковую выборку; не использовать вероятностное требование «два random запуска всегда различны».

- [ ] **6. Запустить `.\mvnw.cmd test`.** Все unit-тесты проходят без Docker и реальной папки `77`. Если есть Git: адресно добавить файлы Task 2 и `git commit -m "feat: sample GAR addresses with streaming XML"`.

## Task 3: Добавление без дублей, mapping, ограничения и отдельная очистка

**Files:** создать `address/AddressStore.java`, дополнить `Address.java`, `AddressRepository.java`, `AddressStoreIT.java`; создать `ImportResult.java`.

**Interfaces:** `AddressStore.append(List<AddressRow> candidates, RandomGenerator random): ImportResult`, `AddressStore.clear(): void`; repository `lockForWrite(): void`, `findAllHouseGuids(): List<UUID>`, `insertIfAbsent(AddressRow row): int`; `ImportResult(int requested, int imported, long total, boolean exhausted)`.

- [ ] **1. Зафиксировать Entity и DTO с согласованными типами и аннотациями.**

```java
@Entity
@Table(name = "addresses", schema = "public")
@Access(AccessType.FIELD)
public class Address {
    @Id
    @Column(name = "house_guid", nullable = false, updatable = false)
    private UUID houseGuid;
    @Column(name = "city", nullable = false, columnDefinition = "text")
    private String city;
    @Column(name = "full_address", nullable = false, columnDefinition = "text")
    private String fullAddress;
    @Column(name = "street", nullable = false, columnDefinition = "text")
    private String street;
    @Column(name = "house_number", nullable = false, columnDefinition = "text")
    private String houseNumber;
    @Column(name = "additional_number_1", columnDefinition = "text")
    private String additionalNumber1;
    @Column(name = "additional_type_1")
    private Integer additionalType1;
    @Column(name = "additional_number_2", columnDefinition = "text")
    private String additionalNumber2;
    @Column(name = "additional_type_2")
    private Integer additionalType2;
    @Column(name = "house_type")
    private Integer houseType;
    @Column(name = "street_object_id", nullable = false)
    private long streetObjectId;
    @Column(name = "street_guid", nullable = false)
    private UUID streetGuid;
    @Column(name = "house_object_id", nullable = false)
    private long houseObjectId;
    protected Address() {}
    public UUID getHouseGuid() { return houseGuid; }
}

public record ImportResult(int requested, int imported, long total, boolean exhausted) {}
```

Все аннотации Entity — `jakarta.persistence.*`. `AddressRow` остаётся immutable record из Task 2 со всеми 13 полями. DTO не помечать `@Entity`, `@Id`, `@GeneratedValue`; Entity не возвращать из контроллера. Входного DTO нет: POST без тела, размер порции фиксирован сервером. DTO ответа не требует `@Valid`: это исходящие вычисляемые счётчики. Native insert обходит JPA lifecycle и Bean Validation, поэтому корректность AddressRow проверяется reader до транзакции и ограничениями SQL, а не надеждой на `@NotBlank` у Entity.

PK `house_guid` обеспечивает уникальность и NOT NULL. `@Column(nullable=false)` совпадает с DDL, но DDL создаётся init SQL. Не добавлять UNIQUE на улицу, номер дома, full_address или house_object_id: ключ дубля — только GUID. Не добавлять FK на street_guid: таблицы улиц нет. Существующий CHECK `addresses_nonempty` и NOT NULL сохраняются; они не мешают добавлению новых адресов. Запретить `@GeneratedValue`, дополнительный id, `@Version` и каскадные связи: для них нет согласованных столбцов.

- [ ] **2. Написать failing тест добавления.** Test helper `rows(int count, long start)` возвращает ArrayList из `LongStream.range(start,start+count)`, создавая для каждого n `new AddressRow("Москва", "г. Москва, ул. Тестовая, д. " + n, "ул. Тестовая", String.valueOf(n), null, null, null, null, 2, 2, new UUID(0,2), n, new UUID(0,n))`.

```java
@Test
void appendsOneHundredMoreWithoutChangingExistingRows() {
    var candidates = rows(250, 1000);
    assertThat(store.append(candidates, new Random(1)))
        .isEqualTo(new ImportResult(100, 100, 100, false));
    var before = jdbc.queryForList("select * from addresses order by house_guid");
    assertThat(store.append(candidates, new Random(2)))
        .isEqualTo(new ImportResult(100, 100, 200, false));
    assertThat(repository.count()).isEqualTo(200);
    assertThat(jdbc.queryForList("select * from addresses order by house_guid"))
        .containsAll(before);
}
```

Запуск: `.\mvnw.cmd -Dit.test=AddressStoreIT verify`; до реализации тест не проходит.

- [ ] **3. Реализовать Spring Data native INSERT с явным конфликтным ключом.**

```java
public interface AddressRepository extends JpaRepository<Address, UUID> {
    @Modifying
    @Query(value = "LOCK TABLE public.addresses IN EXCLUSIVE MODE", nativeQuery = true)
    void lockForWrite();

    @Query("select a.houseGuid from Address a")
    List<UUID> findAllHouseGuids();

    @Modifying
    @Query(value = """
        INSERT INTO public.addresses (
            city, full_address, street, house_number,
            additional_number_1, additional_type_1,
            additional_number_2, additional_type_2, house_type,
            street_object_id, street_guid, house_object_id, house_guid
        ) VALUES (
            :#{#row.city()}, :#{#row.fullAddress()}, :#{#row.street()}, :#{#row.houseNumber()},
            :#{#row.additionalNumber1()}, :#{#row.additionalType1()},
            :#{#row.additionalNumber2()}, :#{#row.additionalType2()}, :#{#row.houseType()},
            :#{#row.streetObjectId()}, :#{#row.streetGuid()},
            :#{#row.houseObjectId()}, :#{#row.houseGuid()}
        ) ON CONFLICT (house_guid) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("row") AddressRow row);
}
```

Импорты аннотаций repository: `org.springframework.data.jpa.repository.Modifying`, `Query`, `JpaRepository`; `org.springframework.data.repository.query.Param`. Возврат insert — реальное affected row count: 1 вставлен, 0 дубль. Проверить binding UUID, nullable Integer и String на PostgreSQL интеграционным тестом, включая обе null дополнительные части.

`save()/saveAll()` не применять в импортном пути: assigned UUID может вести к merge и обновлению старой строки. Не ловить duplicate-key exception внутри транзакции: ошибка SQL может перевести её в aborted state. Пропуск обеспечивает сам PostgreSQL через ON CONFLICT. CHECK/NOT NULL ошибки не скрываются этим механизмом и остаются настоящими ошибками данных.

Основания: [PostgreSQL 18 INSERT / ON CONFLICT](https://www.postgresql.org/docs/18/sql-insert.html), [Spring Data modifying queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html), [Spring Data entity persistence](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/entity-persistence.html).

- [ ] **4. Реализовать атомарное добавление и независимую очистку.**

```java
@Service
public class AddressStore {
    private final AddressRepository repository;
    public AddressStore(AddressRepository repository) { this.repository = repository; }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ImportResult append(List<AddressRow> candidates, RandomGenerator random) {
        repository.lockForWrite();
        Set<UUID> excluded = new HashSet<>(repository.findAllHouseGuids());
        var sample = new ReservoirSampler<AddressRow>(100, random);
        for (AddressRow row : candidates) {
            if (excluded.add(row.houseGuid())) sample.accept(row);
        }
        int inserted = 0;
        for (AddressRow row : sample.values()) inserted += repository.insertIfAbsent(row);
        return new ImportResult(100, inserted, repository.count(), inserted < 100);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void clear() {
        repository.lockForWrite();
        repository.deleteAllInBatch();
    }
}
```

`@Transactional` и `Isolation` — Spring transaction annotations. Методы вызываются через отдельный bean proxy, без self-invocation. Reader до входа в append завершил чтение и валидацию XML. Выборка выполняется из уникальных GUID, отсутствующих в БД после получения блокировки: при наличии 100 новых кандидатов результат ровно +100, даже если другой экземпляр приложения только что импортировал часть каталога. `EXCLUSIVE` блокирует других writers до commit, разрешает обычные SELECT. Конкурентный DELETE пользуется той же блокировкой; результат соответствует порядку завершившихся операций, приоритет DELETE не обещать.

Внутри импортной транзакции нет DELETE/TRUNCATE/UPDATE. Сохранённые данные не перезаписываются. ON CONFLICT — защита repository при прямом повторном insert; в append с блокировкой и исключением GUID конкурирующий дубль до commit возникнуть не должен. Остаток меньше 100 — сохранить остаток, HTTP 200; остаток 0 — imported=0, без ошибки и бесконечных попыток добора. `exhausted=true` означает, что запрос не смог набрать 100 новых из прочитанного набора; это не обещание наличия или отсутствия данных в будущей версии XML. `total` — количество в БД на момент текущей транзакции, оно может измениться после commit из-за следующего запроса.

- [ ] **5. Проверить дубли, исчерпание, rollback и SQL-ограничения.**

```java
@Test
void returnsRemainderThenZero() {
    var candidates = rows(150, 1000);
    store.append(candidates, new Random(1));
    assertThat(store.append(candidates, new Random(2)))
        .isEqualTo(new ImportResult(100, 50, 150, true));
    assertThat(store.append(candidates, new Random(3)))
        .isEqualTo(new ImportResult(100, 0, 150, true));
}
@Test
void duplicateInsertReturnsZeroAndKeepsOriginal() {
    var row = rows(1, 1000).getFirst();
    var changed = new AddressRow(row.city(), "другой полный адрес", row.street(),
        row.houseNumber(), null, null, null, null, row.houseType(),
        row.streetObjectId(), row.streetGuid(), row.houseObjectId(), row.houseGuid());
    transactionTemplate.executeWithoutResult(status -> {
        assertThat(repository.insertIfAbsent(row)).isEqualTo(1);
        assertThat(repository.insertIfAbsent(changed)).isZero();
    });
    assertThat(jdbc.queryForObject("select full_address from addresses where house_guid=?",
        String.class, row.houseGuid())).isEqualTo(row.fullAddress());
    assertThat(repository.count()).isEqualTo(1);
}
@Test
void rollsBackEarlierNewInsertsWhenLaterInsertFails() {
    store.append(rows(100, 1000), new Random(1));
    var before = jdbc.queryForList("select * from addresses order by house_guid");
    var broken = rows(100, 2000);
    var r = broken.getLast();
    broken.set(99, new AddressRow(r.city(), "", r.street(), r.houseNumber(),
        null, null, null, null, r.houseType(), r.streetObjectId(), r.streetGuid(),
        r.houseObjectId(), r.houseGuid()));
    assertThatThrownBy(() -> store.append(broken, new Random(2)))
        .isInstanceOf(DataAccessException.class);
    assertThat(jdbc.queryForList("select * from addresses order by house_guid"))
        .isEqualTo(before);
}
@Test
void onlyClearDeletesDataAndIsRepeatable() {
    store.append(rows(100, 1000), new Random(1));
    store.clear(); store.clear();
    assertThat(repository.count()).isZero();
}
```

`rows()` возвращает изменяемый список. Перед каждым IT очищать только временную test-базу. Тестовый класс не помечать @Transactional: проверяются настоящие service commits. `transactionTemplate` — injected Spring TransactionTemplate для прямого вызова modifying repository.

Дополнительные проверки:
- Дважды один GUID внутри candidates: один кандидат, imported считается по вставленным строкам; заменить full_address дубля — сохранена первая строка.
- Несколько домов на одной улице с одинаковым house_number и разными GUID допустимы: дополнительных UNIQUE нет.
- `house_guid=null` — NOT NULL violation; пробельные city/full_address/street/house_number — CHECK violation. Тестировать каждую ошибку в отдельной транзакции.
- Null в дополнительных номерах/типах round-trip сохраняется null; `house_guid` остаётся UUID без генерации.
- Два параллельных append с одним каталогом из 250 домов → оба imported=100, итог 200 разных GUID. Синхронизация start latch, отдельные service transactions, timeout 10 сек, без Thread.sleep. Подтверждение блокировки отдельным тестом: держать LOCK в JDBC connection, worker ждёт по pg_locks, release → успешный append.

- [ ] **6. Выполнить `.\mvnw.cmd -Dit.test=AddressStoreIT verify`.** Если Git есть, адресно добавить файлы Task 3 и commit `feat: append addresses and skip duplicate house GUIDs`.

## Task 4: REST-контроллер, сервис и счётчики импорта

**Files:** создать `AddressImportService.java`, `AddressController.java`, `AddressExceptionHandler.java`, `OperationInProgressException.java`, `AddressImportServiceTest.java`, `AddressApiIT.java`; использовать `ImportResult.java` из Task 3.

**Interfaces:** `AddressImportService.importAddresses(): ImportResult`, `clearAddresses(): void`; constructor `(GarAddressReader reader, AddressStore store, Path directory)` для теста. Production constructor с `@Autowired` принимает reader, store и `@Value("${repairradar.gar.directory}") String directory`, делегирует Path.of(directory) в тестовый конструктор.

- [ ] **1. Написать тест: ошибка чтения не вызывает storage.**

```java
@Test
void doesNotTouchDatabaseWhenXmlFails() {
    var reader = mock(GarAddressReader.class);
    var store = mock(AddressStore.class);
    var service = new AddressImportService(reader, store, Path.of("77"));
    when(reader.readCandidates(Path.of("77")))
        .thenThrow(new GarImportException("GAR_XML_INVALID", "Broken XML"));
    assertThatThrownBy(service::importAddresses).isInstanceOf(GarImportException.class);
    verifyNoInteractions(store);
}
```

- [ ] **2. Реализовать сервис.** Singleton @Service с constructor injection. GarAddressReader зарегистрировать @Component, без изменяемого request-state в полях.

```java
private final AtomicBoolean busy = new AtomicBoolean();
public ImportResult importAddresses() {
    if (!busy.compareAndSet(false, true)) throw new OperationInProgressException();
    try {
        var candidates = reader.readCandidates(directory);
        return store.append(candidates, RandomGenerator.getDefault());
    } finally { busy.set(false); }
}
public void clearAddresses() {
    if (!busy.compareAndSet(false, true)) throw new OperationInProgressException();
    try { store.clear(); }
    finally { busy.set(false); }
}
```

`OperationInProgressException extends RuntimeException`, сообщение `Address operation is already running`. 409 означает одновременно работающую операцию одного процесса, не дубль адреса. Блокировка БД защищает экземпляры приложения между собой. При malformed XML нет записи. Обычный повторный вызов после завершения предыдущего всегда проходит guard.

- [ ] **3. Реализовать контроллер и обработчик ошибок.**

```java
@RestController
@RequestMapping("/api/addresses")
public class AddressController {
    private final AddressImportService service;
    public AddressController(AddressImportService service) { this.service = service; }
    @PostMapping("/import")
    public ImportResult importAddresses() { return service.importAddresses(); }
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAddresses() { service.clearAddresses(); }
}
```

Первый POST: 200 `{"requested":100,"imported":100,"total":100,"exhausted":false}`; второй: 200 `{"requested":100,"imported":100,"total":200,"exhausted":false}`. Если новых осталось 37 при total=200: 200 `{"requested":100,"imported":37,"total":237,"exhausted":true}`. Все уже есть: 200 `{"requested":100,"imported":0,"total":237,"exhausted":true}`.

`@RestControllerAdvice`: `GAR_SOURCE_INVALID`, `GAR_XML_INVALID` → 422 ProblemDetail; OperationInProgressException → 409 / ADDRESS_OPERATION_IN_PROGRESS; DataAccessException → 503 / DATABASE_ERROR. Недостаток новых адресов и дубли не являются ошибками. Не вводить GAR_NOT_ENOUGH_ADDRESSES. Непредвиденная программная ошибка → 500; подробности SQL/stacktrace/пути в ответ не включать.

```java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(
    HttpStatus.UNPROCESSABLE_ENTITY, "Address source could not be imported");
problem.setTitle("Address import failed");
problem.setProperty("code", exception.code());
return problem;
```

- [ ] **4. Проверить API целиком на PostgreSQL Testcontainers.** `AddressApiIT`: @SpringBootTest(webEnvironment=RANDOM_PORT), JDK HttpClient, fixture на 250 домов, `@DynamicPropertySource` для datasource и GAR_DIRECTORY, отдельная test-база. Разобрать JSON Jackson ObjectMapper из Boot, а не сравнивать порядок полей.

```java
var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/addresses/import"))
    .POST(HttpRequest.BodyPublishers.noBody()).build();
for (int expected : new int[]{100, 200, 250, 250}) {
    long before = repository.count();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    var result = objectMapper.readValue(response.body(), ImportResult.class);
    assertThat(result.requested()).isEqualTo(100);
    assertThat(result.imported()).isEqualTo((int) (expected - before));
    assertThat(result.total()).isEqualTo(expected);
    assertThat(result.exhausted()).isEqualTo(result.imported() < 100);
    assertThat(repository.count()).isEqualTo(expected);
}
var delete = HttpRequest.newBuilder(URI.create(baseUrl + "/api/addresses")).DELETE().build();
var response = client.send(delete, HttpResponse.BodyHandlers.ofString());
assertThat(response.statusCode()).isEqualTo(204);
assertThat(response.body()).isEmpty();
assertThat(repository.count()).isZero();
```

После удаления снова POST → 100: ранее удалённые адреса снова доступны. Испортить XML после первого импорта → 422 и полный snapshot БД неизменен. Через mock проверить 409 busy и 503 DB error без SQL в ответе. Unit-test с CountDownLatch блокирует reader в первом запросе: второй import/clear получает OperationInProgressException, освобождение latch в finally; после завершения/ошибки reader новый запрос проходит. Не считать дубль поводом для 409.

- [ ] **5. Выполнить `.\mvnw.cmd verify`.** Если Git есть: адресно добавить файлы Task 4, commit `feat: expose additive address import counters and cleanup`.

## Task 5: Postman, инструкции и проверка на реальном наборе

**Files:** создать `postman/RepairRadar.postman_collection.json`, завершить `README.md`.

**Interfaces:** collection variable `baseUrl=http://localhost:8080`; два endpoint из Task 4; POST синхронный и может читать файлы несколько минут.

- [ ] **1. Создать импортируемую Postman v2.1 коллекцию.**

```json
{
  "info": {
    "name": "RepairRadar",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [{"key": "baseUrl", "value": "http://localhost:8080", "type": "string"}],
  "item": [
    {
      "name": "Add up to 100 new random addresses",
      "request": {"method": "POST", "header": [], "url": "{{baseUrl}}/api/addresses/import",
        "description": "Reads local GAR XML, then atomically adds up to 100 new addresses. Existing house GUIDs are skipped and stored rows stay unchanged. No request body. May take several minutes."},
      "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": [
        "pm.test('Import counters are consistent', function () { pm.response.to.have.status(200); const r = pm.response.json(); pm.expect(r.requested).to.eql(100); pm.expect(r.imported).to.be.within(0, 100); pm.expect(r.exhausted).to.eql(r.imported < r.requested); pm.expect(r.total).to.be.at.least(r.imported); });"
      ]}}]
    },
    {
      "name": "Delete all addresses",
      "request": {"method": "DELETE", "header": [], "url": "{{baseUrl}}/api/addresses",
        "description": "Deletes every row in the address table."},
      "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": [
        "pm.test('Table cleared', function () { pm.response.to.have.status(204); pm.expect(pm.response.text()).to.eql(''); });"
      ]}}]
    }
  ]
}
```

README: Postman Request timeout выставить 0 либо достаточно большой; автоматическое выполнение всей коллекции завершается очисткой таблицы. Таймаут клиента не гарантирует отмену server-side импорта. Не повторять запрос автоматически при таймауте.

- [ ] **2. Написать README с последовательностью запуска и конфигурацией.**

```powershell
Copy-Item .env.example .env
docker compose up -d --wait
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

В другом терминале:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/addresses/import
docker compose exec -T postgres psql -U repairradar -d repairradar -c "select count(*), count(distinct house_guid) from addresses;"
docker compose exec -T postgres psql -U repairradar -d repairradar -c "select full_address, house_guid from addresses limit 5;"
Invoke-WebRequest -Method Delete -Uri http://localhost:8080/api/addresses -UseBasicParsing
```

Документировать `GAR_DIRECTORY`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`; schema mapping; коды ошибок; известные типы и fallback неизвестных; синхронное чтение; semantics новой выборки; required XML одной даты; тесты требуют Docker. Для запуска jar: `.\mvnw.cmd package` и `java -jar target/repairradar-0.0.1-SNAPSHOT.jar` из корня проекта.

Объяснить init SQL: запускается только на пустом volume. `docker compose down` сохраняет volume; при несовместимой старой схеме Hibernate прекращает запуск. Не предлагать автоматическое `down -v` как исправление. Исправления существующей схемы требуют явной миграции; Flyway для первоначальной единственной таблицы не вводить параллельно init SQL.

- [ ] **3. Проверить реальные XML и ограничение памяти.** Запускать приложение с `java -Xmx512m -jar target/repairradar-0.0.1-SNAPSHOT.jar`. Выполнить один POST, записать длительность и результат `count=100`, `distinct=100`, вручную проверить 5 full_address с исходными атрибутами. Если 512 МБ недостаточно, оптимизировать compact maps либо измеренно повысить лимит и указать факт; не обещать проверенную работу на 512 МБ без успешного прогона. Запрос не должен использовать CSV или Python. Повторить POST: ровно 200 строк, если новых кандидатов достаточно. Все прежние 100 GUID и их поля сохранены; добавленные GUID не пересекаются с прежними.

- [ ] **4. Проверить повторный запуск контейнера без потери данных.** После двух успешных POST:

```powershell
docker compose restart postgres
docker compose up -d --wait
docker compose exec -T postgres psql -U repairradar -d repairradar -c "select count(*) from addresses;"
```

После двух POST ожидается 200. DELETE проверить после этого; ожидается 0, повторный DELETE — 204. После проверки снова выполнить POST, чтобы итоговая локальная база содержала 100 адресов для просмотра пользователем. Не трогать чужие volumes и не удалять исходники.

- [ ] **5. Финальная проверка артефактов.**

```powershell
.\mvnw.cmd verify
Get-Content postman/RepairRadar.postman_collection.json -Raw | ConvertFrom-Json
jar tf target/repairradar-0.0.1-SNAPSHOT.jar
```

Jar не содержит `77/`, исходных многогигабайтных XML и CSV. Postman collection проверена на синтаксис; если Postman/Newman доступны, выполнить на тестовой базе. Если нет — явно указать, что HTTP проверен интеграционными тестами, а импорт коллекции GUI не выполнялся. Финальный отчёт содержит версии, выполненные тесты, результат реального импорта, команды запуска и ссылки на Compose/README/коллекцию. Если есть Git: адресно добавить файлы Task 5 и `git commit -m "docs: add Postman collection and RepairRadar runbook"`.

## Самопроверка плана

- [x] PK/NOT NULL/CHECK, все 13 @Column, UUID/@Id и nullable Integer согласованы с DDL; DTO ответа содержит фактические счётчики.
- [x] Импорт не использует deleteAllInBatch/save/merge; дубли обрабатываются ON CONFLICT и не вызывают ошибку. Очистка вызывается только DELETE endpoint.
- [x] Тесты покрывают 100→200, сохранение старых полей, дубль с изменёнными полями, остаток 50/0 и параллельный импорт.

- [x] Все шесть исходных пунктов покрыты: Compose/DDL — Task 1; Maven/Spring — Task 1; XML/service/Spring Data — Task 2–4; Postman — Task 5; очистка — Task 3–4.
- [x] Уточнения включены: английские поля, `house_guid UUID PRIMARY KEY`, добавление 100 + 100 = 200, пропуск дублей, Postman.
- [x] Нет случайной зависимости от оставшихся Python-скриптов и от Spring-старта для создания таблицы.
- [x] Ошибка XML возникает до изменения БД; ошибка SQL откатывает новые вставки текущего запроса, старые строки сохраняются.
- [x] Пять Review Focus сопоставлены с проверками в задачах.
- [x] Ограничения явно обозначены: отсутствующие справочники типов, синхронное чтение, память индексов, существующий volume, недоступные навыки исполнения.
- [x] План не заявляет, что реализация или её тесты уже выполнены.

## Передача к реализации

Рекомендуется последовательная реализация одним исполнителем: пять задач тесно связаны Java-контрактами и общей схемой. После реализации — отдельное ревью изменений, если доступно в согласованном режиме. Альтернатива — отдельные агенты на задачи с промежуточным ревью. Способ исполнения выбирает пользователь после просмотра плана; документация навыка writing-plans требует этого шага перед реализацией.

# Получение списка и содержимого чатов Telegram с помощью TDLib (на примере Kotlin)

Когда я стал решать задачу получения сообщений из Telegram для последующего анализа, понял -
не хватает простого и понятного введения во взаимодействие с Telegram в роли клиента, а не бота.
Официальная документация отличается от привычной мне документации в мире Java
и Rust как по подаче, так и по качеству. А большинство статьей по запросу "how to load chats from telegram"
отсылают к высокоуровневым библиотекам на Python.

Главная цель статьи дать основу для разработки приложений на Telegram на среднем уровне абстракции TDLib.
В ней я постарался раскрыть как надо читать документацию мессенджера, какие существуют способы взаимодействия с платформой
 и по каким принципам спроектировано API. Во многих местах я не буду давать детальное описание всех параметров, предоставив вместо
этого ссылку на документацию. Задача статьи - дать фундамент для разработки своих сценариев.

Взаимодействие описывается на уровне общения сообщениями и информация из нее будет полезна при использовании любого
языка программирования.


Детально раскрыты следующие сценарии взаимодействия:
* Вход в Telegram зарегистрированным пользователем.
* Получение списка чатов их их типизация (каналы, формы и т.п.).
* Получение сообщение из чата.

# Термины

В статье я буду использовать следующие термины:

* **Пользователь** - человек, который работает с Telegram. Читает чаты, пишет сообщения и т.п.
* **Клиент** - приложение, через которое пользователь работает с Telegram. Например, Telegram Desktop.
* **Сервер** - backend часть Telegram.
* **Сообщение** - информация передаваемая от клиента к серверу и от сервера к клиенту.

Для сообщений описываемых сценариев будет дана ссылка на документацию. Для сообщений, которые используются в качестве примера, ссылки может не быть.

# Варианты взаимодействия с Telegram

## Протокол MTProto

![](https://habrastorage.org/webt/th/tt/-0/thtt-0clo7er9veeh2ocppnjohq.png)

Все взаимодействие с Telegram осуществляется через его собственный протокол
[MTProto Mobile Protocol](https://core.telegram.org/mtproto). Протокол описывает не только бизнес-сообщения, но и
сетевое взаимодействия. В документации приводится такая условная связь протокола с уровнями [сетевой модели OSI](https://ru.wikipedia.org/wiki/%D0%A1%D0%B5%D1%82%D0%B5%D0%B2%D0%B0%D1%8F_%D0%BC%D0%BE%D0%B4%D0%B5%D0%BB%D1%8C_OSI).

1. Физический уровень (передача по проводам) - используется как есть.
2. Канальный уровень (физическая адресация, идентификация по MAC) - используется как есть.
3. Сетевой уровень (логическая адресация, идентификация по IP) -  используется как есть.
4. Транспортный уровень: собственная реализация упаковки пакетов. Здесь есть условность, т.к. по сути разработчики
    Telegram полагаются на другие протоколы при доставки своих сообщений - TCP, WebSockets, HTTP/HTTPS. Используется их
    инфраструктура, а не семантика. MTProto описывает свой формат пакетов [MTProto transport protocol](https://core.telegram.org/mtproto#mtproto-transport),
     который поддерживает шифрование и [обфусикацию](https://core.telegram.org/mtproto/mtproto-transports#transport-obfuscation).
5. Сессионный уровень: [MTProto session](https://core.telegram.org/mtproto/description#session) - управление stateful-сессиями. Упрощенно, сессия это вход пользователя под клиентом.
6. Уровень представления: формат сообщений, описанный с помощью [TL Language](https://core.telegram.org/mtproto/TL).
7. Прикладной уровень (приложений): [High-Level RPC API](https://core.telegram.org/mtproto#high-level-component-rpc-query-language-api) - определяет набор сообщений и их семантику.

## TDLib

Telegram предоставляет библиотеку TDLib (Telegram Database library), которая закрывает вопросы сетевого взаимодействия между клиентом и сервером (дата центрами Telegram), а также реализует поддержку сессий, кэширование сообщений и сохранение файлов.
Библиотека предоставляет все сообщения протокола  и методы их отправки и получения. TDLib написан на C++.

Библиотеки по работе с Telegram для Java/Kotlin, Javascript/Typescript, Python, Rust и т.д. могут реализованы двумя способами:
* Библиотека реализует все уровни MTProto самостоятельно. Пример таких библиотек: [Telethon](https://github.com/LonamiWebs/Telethon) на Python,
  [grammers](https://github.com/Lonami/grammers) на Rust.
* Библиотека реализует обертку (wrapper, bindings) над TDLib. Сами автора TDLib предоставляют такие библиотеки для Java и C#.
  Сообщество поддерживает библиотек для других языков: [tdlib-rs](https://crates.io/crates/tdlib-rs) для Rust, [python-telegram](https://github.com/alexander-akhmetov/python-telegram) и
  [aiottdlb](https://github.com/pylakey/aiotdlib) для Python, [tdlight-java](https://github.com/tdlight-team/tdlight-java/) для Java.

![](https://habrastorage.org/webt/yt/hx/ds/ythxdspdgfkjzmlrdng6w6pxq4y.png)

Помимо реализации слоя вызова функций TDLib с помощью механизмов связывания (binding, native interface и т.п.),
библиотеки предоставляют механизм генерации классов языка программирования из их описания на [TL Language](https://core.telegram.org/mtproto/TL). Также некоторые библиотеки реализуют собственные утилиты и улучшения, облегчающие
разработку конечных приложений.


## Тестовые дата центры

Для разработки крайне рекомендуется использовать тестовые дата центры Telegram. В этом случае гораздо
сложнее нарваться на бан аккаунта из-за лимитов или подозрительного поведения при разработке приложения.

Я не буду много писать про тестовые стенды, т.к. на Хабре есть две отличные статьи:
[Тестовые сервера Telegram](https://habr.com/ru/articles/735552/)  - введение
[Тестовые серверы Telegram: инструкция по эксплуатации](https://habr.com/ru/companies/selectel/articles/763286/) - более подробное описание.

**Важно!** Статьи устарели в плане регистрации аккаунта. С какого-то времени зарегистрировать новый
аккаунт даже на тестовом стенде возможно только по реальному номеру телефона. По крайне мере с IP РФ
и Турции.

Для входа в desktop приложении надо зажать Shift+Alt и кликнуть правой клавишей мышки
на "Добавить аккуант" (Add account) в меню приложения.

![](https://habrastorage.org/webt/76/w0/ph/76w0phaxwanhr73zhq2_2yay7ec.png)


**Галочка верификации у канала/группа**
1. Переходим к обычному созданию канала/группы.
2. Указываем имя/описание
3. Когда вы перешли на этап ссылки, в конце ссылки вам нужно указать _vf
4. Готово! Ваш канал теперь с галочкой!

**Telegram premium**
1. Переходим в @izpremiumbot и нажимаем СТАРТ.
2. Далее вам предложит купить Telegram Premium за 379,00 RUB. Нажимаем Receipt.
3. Нужно ввести следующие данные карты: 4242 4242 4242 4242. Дату (любую), 3 цифры CVC, владелец местоположение и индекс могут быть любыми.
4. Оплачиваем.
5. Готово! Теперь на вашем аккаунте есть премиум-статус.

Две инструкции выше я не проверял.

## Регистрация приложения
Перед подключением к серверам, приложение должно быть зарегистрировано. Подробная инструкция и указания
на "неправильное использование API" описаны [здесь](https://core.telegram.org/api/obtaining_api_id), я не буду их повторять. Результатом регистрации является `api_id` и `api_hash` - они передаются при подключении.

Для разработки тестового приложения мы будем использовать специальный тестовый API.

# Обзор API

## Документация

Стартовая страница с документацией находится по ссылке https://core.telegram.org/api. Внизу страницы
есть ссылки на описание некоторых сценариев. Сообщения написаны на TL Language, так что без привычки
читать их может быть не просто. Я советую обращаться к этим сценариям после изучения данной статьи.

Отдельно разработчики советуют использовать исходный код:
* клиентов [Telegram](https://github.com/telegramdesktop/tdesktop) (написан без TDLib) и
[Telegram X](https://github.com/TGX-Android/Telegram-X) (написан на базе TDLib);
* примеры библиотек из репозитория [TDLib](https://github.com/tdlib/td/blob/master/example/java/org/drinkless/tdlib/example/Example.java).

## Классы

Основным понятием API является класс. Он описывается:
1. Конструктор - число, которое позволяет понять, какому именно классу относится сообщение.
2. Набором полей - что именно содержит класс


На самом деле классы ближе к структурам данных с полями, чем к классам из ООП. Фактически они определяют поля сообщений взаимодействия.
Можно считать, что поведения (методов) они не содержат.

Классы поддерживают наследования. Базовым предком является абстрактный класс [`TIObject`](https://core.telegram.org/tdlib/docs/classtd_1_1_tl_object.html). Нам он не очень интересен, гораздо интереснее два его наследника [`Object`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_object.html) и [`Function`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_function.html). Наследники `Object` это просто наборы полей, а наследники `Function` можно послать в качестве запроса от клиента к серверу. Про них отдельно ниже.

Классы можно просматривать в виде [линейного списка](https://core.telegram.org/tdlib/docs/classes.html) или как [дерево](https://core.telegram.org/tdlib/docs/hierarchy.html) иерархии наследования.


## Функции и обновления

 Наследники `Function` это сообщения, которые можно послать в Telegram. Для каждой функции в документации указано, какого типа сообщение возвращается в ответ. Тип есть в секции Description, но удобнее посмотреть в секции Public Types определение типа ReturnType - в нем будет гиперссылка на возвращаемый тип.
![](https://habrastorage.org/webt/ih/lg/cb/ihlgcbwip2-ommxdvf4f4xlglfi.png)

На скриншоте возвращается сообщение класса [ok](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1ok.html), что является самым распространенным ответом. Можно считать, что это не функция - а процедура. Как правило, отправив такое сообщение мы
инициализируем отдельный поток сообщений от сервера с нужной нам информацией. Функции же возвращают ответ сразу.

Также каждая функция (процедура) может вернуть [`error`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1error.html).  Обычно причиной ошибки является или плохо написанный клиент, или независящая от клиента ошибка, поэтому в большинстве случаев его можно обрабатывать как исключения (exception). Но в некоторых сценариях (например, получение списка чатов) на `error` завязана бизнес-логика. Подробнее про ошибки можно прочитать в [документации](https://core.telegram.org/api/errors), а самые дотошные могут посмотреть [полный список ошибок](https://core.telegram.org/file/400780400111/3/RF-b0LDHWpc.202549.json/9b83afb26f1ba2f8aa) в виде огромного JSON.

Наследники класс [`Update`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_update.html) (сам `Update` наследник класса [`Object`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_object.html)) приходят от сервера к клиенту. `Update` надо
трактовать не как уведомление об изменении состояния пользователя, а как требование изменить состояния клиента. Объясню на примере чата. Сообщение `updateNewChat` не означает, что у пользователя появился новый чат. Оно означает, что клиенту надо вывести информацию о чате.

Важно понимать и помнить:
* Telegram шлет сообщения не только при получение запроса от клиента, но и по своим внутренним событиям. Самый простой пример -
  клиент не запрашивает каждую микросекунду "дай мне новое сообщение в чате". Как только в чате появляется сообщение,
  сервер уведомляет о нем клиента.
* Формально, функция может возвращать [`ok`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1ok.html), но его получение будет для сервера триггером отправки других сообщений. Почти все сценарии строятся на таком поведении. (Хотя мне кажется, чем
более поздняя версия API, тем больше авторы склоняются к использованию функций)

## Перечислимые типы

Часто наследование используется для определения перечислимых типов (enum). Базовый класс определяет имя enumeration, а его наследники
задает значение перечисления. Имя базового класса начинается с заглавной буквы, а имена классов значений начинаются с прописной буквы.
Причем началом имени являтеся имя базового класса (правда, есть и исключения). Например, тип чата имеет базовый класс `ChatType` и наследников `chatTypePrivate`, `chatTypeSupergroup` и др.

## Связанные идентификаторы

TDLib предоставляет гарантию, что основная информация об объекте всегда приходит до того, как приходит уточняющая его информация.
На примере работы с чатом. Можно получиться информацию о чате используя запрос `getChat`, но так делает не нужно. Потому что есть гарантия, что *любое* сообщение, которое имеет отношению к чату, придет *строго после* сообщения с информацией об этом чате. В случае чата это будет сообщение `updateNewChat`. Никогда не будет такого, что придет сообщение `updateNewMessage` (содержит поле `chat_id`) до того, как придет сообщение `updateNewChat`.

# Использование TDLib в приложении на Kotlin

Для пользователей других языков я советую пропустить раздел подключения, но посмотреть раздел "Расширения библиотеки",
чтобы понимать в дальнейшем было проще читать примеры.

## Зависимость Gradle
Я не стал использовать предлагаемый авторами Telegram способ, а вместо это пошел более простым путём и выбрал библиотеку
[tdlight-java](https://github.com/tdlight-team/tdlight-java?tab=readme-ov-file). Её нет в стандартных репозиториях типа
Maven Central, поэтому нужно добавить репозиторий   https://mvn.mchv.eu/repository/mchv/. Также я сразу подключил logback
и корутины. Запускать я буду на Arch Linux, это важно при выборе классификатора. Если вы запускаете не под linux, посмотрите
[документацию](https://github.com/tdlight-team/tdlight-java?tab=readme-ov-file#-native-dependencies) к библиотеке и укажите нужный классификатор.

Итоговый файл выглядит так:
```
val tdlightVersion = "3.4.4+td.1.8.52"

plugins {
    kotlin("jvm") version "2.1.10"
}

group = "com.vygovskiy"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
    maven("https://mvn.mchv.eu/repository/mchv/")
}

dependencies {
    implementation(platform("it.tdlight:tdlight-java-bom:$tdlightVersion"))
    implementation(group = "it.tdlight", name = "tdlight-java")
    implementation(group = "it.tdlight", name = "tdlight-natives", classifier = "linux_amd64_gnu_ssl3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("ch.qos.logback:logback-classic:1.5.20")
    implementation("io.github.oshai:kotlin-logging:7.0.13")
}

kotlin {
    jvmToolchain(21)
}
```

TDLight Java опирается на форк TDLib, который называется (сюрприз) [TDLight](https://github.com/tdlight-team/tdlight). Она лучше оптимизирована для реализации серверных приложений анализа содержимого Telegram, чем TDLight. Например, в ней можно
отключить накопление нотификаций и за счет этого экономить RAM.
Подробнее про отличия есть в [readme](https://github.com/tdlight-team/tdlight?tab=readme-ov-file#tdlight-extra-features).


## Обзор TDLight Java

Библиотека [TDLight Java ](https://github.com/tdlight-team/tdlight-java/) предоставляет два способа работать с TDLib:
* [`SimpleTelegramClient`](https://github.com/tdlight-team/tdlight-java/blob/v3.4.4%2Btd.1.8.52/tdlight-java/src/main/java/it/tdlight/client/SimpleTelegramClient.java) - высокоуровневое API, которое предоставляет методы для аутентификацию и списка загрузки чатов.
* [`TelegramClient`](https://github.com/tdlight-team/tdlight-java/blob/v3.4.4%2Btd.1.8.52/tdlight-java/src/main/java/it/tdlight/TelegramClient.java) - простой интерфейс, реализующий методы инициализации (включая установка обработчиков сообщений) и отправки
  сообщений.

В статье я буду использовать `TelegramClient`, чтобы оставаться как можно ближе к протоколу MTProto. Также не нравится, как реализованы
некоторые методы из `SimpleTelegramClient`. Зачастую они слишком уж simple, а где-то наоборот, переусложнены.

Все сообщения протокол MTProto сгенерированы как внутренние классы класса TdApi. Это огромный класс на несколько десятков, если не сотен, тысяч строк кода.

Рассмотрим класс сообщения на примере [`getMessage`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_message.html).
На что обратить внимание:
* Код сгенерен по тому же описанию, что и раздел с документацией. Описание класса, полей - все один в один.
* Класс является наследником Function, в ответ придет сообщения класса `Message`
* Все поля объявлены публичными, никаких set/get.
* В константе `CONSTRUCTOR` зафиксировано число, по которому можно определить класс сообщения.
* Создано три конструктура - пустой `GetMessage()`, со всеми полями  и `GetMessage(DataInput input)` для считывания сообщения из
  MTProto. В дополнение к последнему конструктору есть методы `serialize(DataOutput output)`
* `hashCode()` и `equals()` рассчитываются по разным полям, но основное правило "если два объекта равны согласно методу equals(), то их хэш-коды тоже должны совпадать" соблюдено. Также стоит обратить внимание, что для `hashCode` используется семантическое поле `chatId`. Это означает, что генерация определяется по конкретным полям и в критических случаях имеет смысл проверять, как именно считается `hashCode`.

```java
public final class TdApi {
 // ... много других классов  ...

  /**
	 * Returns information about a message. Returns a 404 error if the
	 * message doesn't exist.
	 * <p> Returns {@link Message Message} </p>
	**/
	public static final class GetMessage extends Function<Message> {


		/**
		 * Identifier of the chat the message belongs to.
		**/
		public long chatId;

		/**
		 * Identifier of the message to get.
		**/
		public long messageId;

		/**
		 * Identifier uniquely determining type of the object.
		**/
		public static final int CONSTRUCTOR = -1821196160;

		/**
		 * Returns information about a message. Returns a 404 error if the message doesn't exist.
		 *
		 * <p> Returns {@link Message Message} </p>
		**/
		public GetMessage() {}

		/**
		 * Returns information about a message. Returns a 404 error if the message doesn't exist.
		 *
		 * <p> Returns {@link Message Message} </p>
		 *
		 * @param chatId Identifier of the chat the message belongs to.
		 * @param messageId Identifier of the message to get.
		 *
		 * <p> Returns {@link Message Message} </p>
		**/
		public GetMessage(long chatId, long messageId) {
			this.chatId = chatId;
			this.messageId = messageId;
		}

		/**
		 * Returns information about a message. Returns a 404 error if the message doesn't exist.
		 *
		 * <p> Returns {@link Message Message} </p>
		 *
		 * @param input Serialized input
		 * @throws IOException the deserialization failed
		**/
		public GetMessage(DataInput input) throws IOException {
			this.chatId = input.readLong();
			this.messageId = input.readLong();
		}

		/**
		 * @return this.CONSTRUCTOR
		**/
		public int getConstructor() {
			return GetMessage.CONSTRUCTOR;
		}

		/**
		 * Serialize the TDLib class
		 * @param output output data stream
		 * @throws IOException the serialization failed
		**/
		public void serialize(DataOutput output) throws IOException {
			output.writeInt(GetMessage.CONSTRUCTOR);
			output.writeLong(this.chatId);
			output.writeLong(this.messageId);
		}


		public boolean equals(java.lang.Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			GetMessage getMessage = (GetMessage) o;
			if (this.chatId != getMessage.chatId) {
				return false;
			}
			if (this.messageId != getMessage.messageId) {
				return false;
			}
			return true;
		}

		public int hashCode() {
			int result = Long.hashCode(this.chatId);
			return result;
		}
	}

 // ... много других классов  ...
```

## Главные методы
Путей взаимодействия именно с TDLib в TDLight Java не так, чтобы и много и описаны в интерфейсе
[`TelegramClient`](https://github.com/tdlight-team/tdlight-java/blob/v3.4.4%2Btd.1.8.52/tdlight-java/src/main/java/it/tdlight/TelegramClient.java)

* Установка update handlers для обработки обычных сообщений от Telegram, обработки наследников `error` и обработки
  ошибок в обработчике. Все обработчики задаются в момент инициализации клиента (`initialize(...)`)
* Отправка сообщения `send` наследника `Function`.

## Мои расширение библиотеки

Я добавил две функции расширения, которые добавляют семантику "процедура/функция" и расширенное журналирование (логгирование)
отправляемых сообщений.

```kotlin
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import it.tdlight.ExceptionHandler
import it.tdlight.TelegramClient
import it.tdlight.jni.TdApi
import java.util.concurrent.CompletableFuture

fun logQuery(query: TdApi.Function<*>, log: KLogger) {
    if (log.isDebugEnabled()) {
        val operation = if (query is TdApi.Function) {
            "call"
        } else {
            "send"
        }
        log.debug { "[$operation] ${query::class.simpleName}" }
        log.trace { "[$operation] ${query}" }
    }
}

fun TelegramClient.sendAsProcedure(query: TdApi.Function<*>, exceptionHandler : ExceptionHandler, log: KLogger) {
    logQuery(query,log)

    this.send(query, { response -> response.throwExceptionOnError() }, exceptionHandler)
}

fun TelegramClient.sendAsFunction(query: TdApi.Function<*>,  log: KLogger): CompletableFuture<TdApi.Object> {
    logQuery(query,log)

    val result = CompletableFuture<TdApi.Object>()
    this.send(query) { obj ->
        log.debug { "${query.shortInfo()} -> ${obj.shortInfo()}" }
        log.trace { "$obj" }
        result.complete(obj)
    }
    return result
}
```

Также я добавил несколько функций-расширений класса `TdApi.Object`, который является прозрачным маппингом класса [`Object`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_object.html), в первую очередь для удобной трассировки взаимодействия
клиента и сервера (полный листинг см. в файле `TdApiOjbectExtensions.kt` ):
```kotlin
/**
 * Вывести в лог информацию об ошибки и выкинуть исключение [TelegramError], если Object имеет класс [TdApi.Error]
 */
fun TdApi.Object.throwExceptionOnError()

/**
 * Определяет, что пришел [TdApi.Update] который описывает не интересные для сервера приложения.
 * Используется для оптимизации журналирования.
 */
fun TdApi.Object.isParametersOrOption(): Boolean

/**
 * Выводит краткую важную информацию о классе, основываясь на его типе. Всегда выводится имя класса, а также ключевые поля.
 * Например, для UpdateChatLastMessage выводится chat.id
 */
fun TdApi.Object.shortInfo() : String
```

А также я добавил несколько [type alias](https://kotlinlang.org/docs/type-aliases.html) (файл `TelegramTypeAliases.kt`):
```kotlin
typealias SupergroupId = Long
typealias ChatId = Long
typealias ForumTopicId = Long
```

## Корутины
Я использую [корутины](https://kotlinlang.org/docs/coroutines-basics.html) Kotlin. Очень грубо их можно рассматривать как
возможность использовать async/await. Если вы пишите на Javascript, TypeScript, Rust - вам должны быть понятны принципы
использования этих процедур. Разработчики на Java могут рассматривать (опять таки, это грубое описание) корутины как удобный
способ работы с [CompletableFeature](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html).

В некоторых сценариях мне важно/удобно/проще организовать последовательную обработку сообщений, для этого я использую [каналы](https://kotlinlang.org/docs/channels.html) .

# Реализация сценариев
Я реализовал тестовое приложение с простым дизайном классов, который точно можно сделать лучше.
Есть два главных класса:
* LvTelegramClient - моя собственная реализации клиента, которая предоставляет методы входа пользователя,
  получение список чатов и сообщений из чата.
* ChatInformationComposer - обрабатываем сообщения с информацией о чатах и реализует логику определения
  типа чата и связывание чатов между собой (например, чат канала с чатом прямых сообщений автору канала).

LvTelegramClient получился несколько сумбурным, прошу простить и понять - стояла задача изучить и понять протокол,
а не сделать собственную реализацию клиента.

Также важно, что иногда я буду вставлять примеры кода из *разных* частей приложения. Так я сделал для наглядности
текста. В этом случае я буду указывать, где находятся части кода в примере.

## Инициализация библиотеки

Инициализация библиотеки заключаются в следующих шагах:
1. Загрузка библиотеки и binding к ней через JNI, настройка журналирования.
2. Реализация обработчика сообщений и создание клиента

**Первый шаг** с использованием TDLight Java выглядит так:
```kotlin
import io.github.oshai.kotlinlogging.KotlinLogging
import it.tdlight.Slf4JLogMessageHandler
import it.tdlight.client.APIToken
import it.tdlight.jni.TdApi
import it.tdlight.util.LibraryVersion

fun main(args: Array<String>) = runBlocking {
    val log = KotlinLogging.logger {  }
    it.tdlight.Init.init()
    it.tdlight.Log.setLogMessageHandler(2, Slf4JLogMessageHandler())


  // <... skipped ... >
}
```

В журналировании важно понять, в TDLib есть свои уровни и мы делаем микс управления - задаем
уровень вывода информации в TDLib и можем также управлять выводом через Slf4J (имя логгера `it.tdlight.TDLight`)

| Уровень TDLib | Уровень Slf4J | Описание |
|---------------|---------------|----------|
| -1, 0, 1 | error | Фатальные ошибки |
| 2 | warn | Ошибки |
| 3 | info | Предупрждения |
| 4 | debug | Отладочные сообщения |
| 5 .. 1024 | trace | Детальные отладочные сообщения |


**Реализация обработчика и создание клиента**. Под обработчиком я имею ввиду ту функцию или метод,
которая будет непосредственно получать сообщения от TDLib и, скорее всего, их маршрутизировать.
Код создания и инициализация клиента выглядит так (см. конструктор класса
`LvTelegramClient` и `LvTelegramClient.login`):

```kotlin
val clientFactory = ClientFactory.create()
client = clientFactory.createClient()

client.initialize(this::handleUpdate, this::handleUpdateException, this::handleDefaultException)
```
Ключевая функция `initialize` принимает три обработчика сообщений. Я минимизировал `handleUpdate` -
ее задача вывести сообщение в лог и отправить в правильный обработчик. В нашем случае их два - вход
пользователя и получение информации о чатах. Функция журналирования сообщений выводит не все их, а только важные для меня: сообщения связанные с входом пользователя [`UpdateAuthorizationState`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_authorization_state.html) и сообщения, нужные для получения чатов.

```kotlin
private fun handleUpdate(update: TdApi.Object) = coroutineScope.launch {
  logUpdate(update)
  when (update) {
      is TdApi.UpdateAuthorizationState -> {
          // канал направит сообщение в processAuthorization(update)
          authorizationChannel.send(update)
      }

      is TdApi.UpdateSupergroup,
      is TdApi.UpdateNewChat -> {
          chats.processUpdate(update)
      }
  }
}
```

*Примечание к моей реализации* Для простоты (на самом деле это не обязательно), я обрабатываю все сообщение по авторизации через каналы. В конструкторе `LvTelegramClient` задана настройка канала, которая для каждого входящего сообщения в канале вызывает функцию `processAuthorization`

```kotlin
  coroutineScope.launch {
      for (authorizationUpdate in authorizationChannel) {
          processAuthorization(authorizationUpdate)
      }
  }
```

Обработчики ошибок очень просты и отличаются только сообщением в логах. Вполне достаточно для обучающего приложения.

```kotlin
private fun handleUpdateException(ex: Throwable) = runBlocking {
  log.debug { "[handle exception] ${ex::class.simpleName}" }
  log.trace(ex) {}
}

private fun handleDefaultException(ex: Throwable) = runBlocking {
  log.debug { "[default exception] ${ex::class.simpleName}" }
  log.trace(ex) {}
}
```

## Вход пользователя
### Сценарий
Вход пользователя это разовая процедура, которая помогает получить новую сессию на новом устройстве.
TDLib  берет на себя хранение информации о сессии, поэтому повторять процедуру входа при каждом запуске
приложения не придется. Полное описание входа есть в официальной [документации](https://core.telegram.org/api/auth), а базовое показано в официальном [примере](https://github.com/tdlib/td/blob/master/example/java/org/drinkless/tdlib/example/Example.java#L90). Я покажу и расскажу про промежуточный вариант, без регистрации и входа по QR. На диаграмме в прямоугольниках показаны состояния сервера, а рядом со стрелочками показаны сообщения для перехода в другой состояние. Сервер передает состояния
путем отправки сообщений [`updateAuthorizationState`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_authorization_state.html) в поле которого передается новое состояние.

![](https://habrastorage.org/webt/bj/yt/aw/bjytawwuk_rhdozwu-1qiwnowfy.png)

После инициализации TDLib передает сообщение `updateAuthorizationState` с полем `authorizationState` типа `authorizationStateWaitTdlibParameters`. Нам нужно сформировать и отправить сообщение [`setTdlibParameters`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1set_tdlib_parameters.html)

```kotlin
private fun processAuthorization(update: TdApi.UpdateAuthorizationState) = coroutineScope.launch {
  try {
      val state = update.authorizationState
      log.debug { "authorizationState = ${state::class.simpleName}" }
      when (state) {
          is TdApi.AuthorizationStateWaitTdlibParameters -> {
              val apiToken = APIToken.example();
              val sessionPath = Path("sessions","example2")

              val settings = TdApi.SetTdlibParameters().apply {
                useTestDc = true
                apiId = apiToken.apiID      // 94575
                apiHash = apiToken.apiHash  // a3406de8d171bb422bb6ddf3bbd800e2

                databaseDirectory = sessionPath.resolve("data").toString()
                filesDirectory = sessionPath.resolve("downloads").toString()
                useFileDatabase = true
                useChatInfoDatabase = true
                useMessageDatabase = true

                useSecretChats = false
                databaseEncryptionKey = null

                systemLanguageCode = Locale.US.displayLanguage
                deviceModel = "Desktop ${System.getProperty("os.name", "unknown")}"
                systemVersion = "${System.getProperty("os.version", "unknown")}"
                applicationVersion = "0.1 (${LibraryVersion.IMPLEMENTATION_NAME}  ${LibraryVersion.VERSION})"
              }
              sendAsProcedure(settings) // в ответ приходит ok, поэтому шлем как процедуру
          }
 <... skipped ...>
}
```

Логически настройки делятся на группы:
* **Подключение** - указываем какой ДЦ использовать (useTestDC), и id/hash для подключения (см выше раздел Регистрация приложения).
* **Настройки TDLib** - выбираем фичи TDLib и задаем пути хранения.
* **Секретные чаты** - будут использоваться секретные чаты или нет, а также ключ шифрования БД. Если секретные
  чаты использовать не планируется, то ключ можно не задавать (как сделано у меня)
* **Информация о клиенте** - ее можно увидеть в списке сессий в клиенте Telegram.

**Обработка входа пользователя**

*Примечание к моей реализации* В приложении-примере нет функции, как написано выше. Настройки формируются в функции `main` и сохраняется в качестве поля объекта `LvTelegramClient`.

Полная процедура входа приведена в листинге ниже. Обратите внимание, что приходящие [AuthorizationState](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_authorization_state.html) это не просто enum, а полноценные объекты разных классов с разными полями.

```kotlin
private fun processAuthorization(update: TdApi.UpdateAuthorizationState) = coroutineScope.launch {
    try {
        val state = update.authorizationState
        log.debug { "authorizationState = ${state::class.simpleName}" }
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                sendAsProcedure(settings)
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                val phoneSettings = TdApi.PhoneNumberAuthenticationSettings().apply {
                    allowFlashCall = false
                    allowMissedCall = false
                    isCurrentPhoneNumber = false
                    hasUnknownPhoneNumber = false
                    allowSmsRetrieverApi = false
                    firebaseAuthenticationSettings = null
                    authenticationTokens = null
                }
                sendAsProcedure(TdApi.SetAuthenticationPhoneNumber(userPhoneNumber, phoneSettings))
            }

            is TdApi.AuthorizationStateWaitCode -> {
                val codeInfo = state.codeInfo
                log.debug { codeInfo.toString() }
                print("!!!!!!!!!!  Enter code for ${state.codeInfo.phoneNumber}: ")
                val code = readln()
                val checkCode = TdApi.CheckAuthenticationCode(code)
                sendAsProcedure(checkCode)
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                print("!!!!!!!! Enter authentication password, hint: ${state.passwordHint}")
                val password = readln()
                val checkPassword = TdApi.CheckAuthenticationPassword(password)
                sendAsProcedure(checkPassword)
            }

            is TdApi.AuthorizationStateReady -> {
                log.debug { "ready to  use private API" }
                loadMe()
            }

            is TdApi.AuthorizationStateClosed -> TODO()
            is TdApi.AuthorizationStateClosing -> TODO()
            is TdApi.AuthorizationStateLoggingOut -> TODO()

            is TdApi.AuthorizationStateWaitEmailAddress -> TODO()
            is TdApi.AuthorizationStateWaitEmailCode -> TODO()
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> TODO()

            is TdApi.AuthorizationStateWaitPremiumPurchase -> TODO()
            is TdApi.AuthorizationStateWaitRegistration -> TODO()
        }
    } catch (e: Throwable) {

    }
}
```

### Классы и сообщения из сценария

| Класс сообщения | Описание из документации |
| :--- | :--- |
| [updateAuthorizationState](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_authorization_state.html) | The user authorization state has changed.
| [checkAuthenticationCode](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1check_authentication_code.html) | Checks the authentication code. Works only when the current authorization state is authorizationStateWaitCode . Returns `object_ptr<Ok>`. |
| [checkAuthenticationPassword](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1check_authentication_password.html) | Checks the 2-step verification password for correctness. Works only when the current authorization state is authorizationStateWaitPassword . Returns `object_ptr<Ok>`. |
| [requestAuthenticationPasswordRecovery](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1request_authentication_password_recovery.html) | Requests to send a 2-step verification password recovery code to an email address that was previously set up. Works only when the current authorization state is authorizationStateWaitPassword . Returns `object_ptr<Ok>`. |
| [setAuthenticationPhoneNumber](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1set_authentication_phone_number.html) | Sets the phone number of the user and sends an authentication code to the user.  Returns `object_ptr<Ok>`. |
| [setTdlibParameters](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1set_tdlib_parameters.html) | Sets the parameters for TDLib initialization. Works only when the current authorization state is authorizationStateWaitTdlibParameters . Returns `object_ptr<Ok>`. |

Обрабатываемые в примере состояния авторизации:
| Состояние авторизации | Описание из документации |
| :--- | :--- |
| [AuthorizationState](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_authorization_state.html) | This class is an abstract base class. Represents the current authorization state of the TDLib client. Returns `object_ptr<Ok>`.
| [authorizationStateWaitTdlibParameters](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1authorization_state_wait_tdlib_parameters.html) | Initialization parameters are needed. Call setTdlibParameters to provide them. Returns `object_ptr<Ok>`. |
| [authorizationStateWaitPhoneNumber](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1authorization_state_wait_phone_number.html) | TDLib needs the user's phone number to authorize. Call setAuthenticationPhoneNumber to provide the phone number, or use requestQrCodeAuthentication or checkAuthenticationBotToken for other authentication options. Returns `object_ptr<Ok>`.|
| [authorizationStateWaitCode](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1authorization_state_wait_code.html) | TDLib needs the user's authentication code to authorize. Call checkAuthenticationCode to check the code. Returns `object_ptr<Ok>`.|
| [authorizationStateWaitPassword](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1authorization_state_wait_password.html) | The user has been authorized, but needs to enter a 2-step verification password to start using the application. Call checkAuthenticationPassword to provide the password, or requestAuthenticationPasswordRecovery to recover the password, or deleteAccount to delete the account after a week. Returns `object_ptr<Ok>`.|
| [AuthorizationStateReady](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1authorization_state_ready.html) | The user has been successfully authorized. TDLib is now ready to answer general requests. Returns `object_ptr<Ok>`.|


## Получение информации о пользователе
### Сценарий
После входа пользователя (а точнее после переходе в состояние авторизации [`authorizationStateReady`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1authorization_state_ready.html)), нам
становятся доступны все взаимодействие с сервером. Первое, с чего хочется начать - получить информацию
о пользователе. Для этого используется сообщение [`getMe`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1authorization_state_ready.html), которое возвращает [`user`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1user.html). В моей интерпретации это функция, и работать с ней просто. Класс `user` передает подробную информацию о пользователе - его id, имя и фамилию, номер телефона, аватар, статус и т.п. Он используется не только для описания текущего пользователя клиента,
но и для описания любого пользователя - например, собеседника в чате.

```
/**
 * Load user and complete [[loginCompletableFuture]]
 */
private suspend fun loadMe() {
    val response = sendAsFunction(TdApi.GetMe()).await()

    when (response) {
        is TdApi.User -> {
            loginCompletableFuture.complete(response)
            gotoNextState()
        }

        is TdApi.Error -> gotoNextState(TelegramError(response))
    }
}
```

### Примечание к моей реализации
Я использую [`CompletableFeature`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html) (в Javascript его ближайший
аналоги это Promise) из-за асинхронной природы получения информации о пользователе. Я не стал прятать
этот факт от пользователя моего класса (вдруг он захочет сделать вход сразу 100 пользователям одновременно) и возвращаю `CompletableFeature` явно.

```kotlin

class LvTelegramClient {
    // храним как информацию о пользователе, так и статус ее получения (магия CompletableFuture)
    val loginCompletableFuture = CompletableFuture<TdApi.User>()

    <... skipped ...>

    fun login(): CompletableFuture<TdApi.User> {
        // запускаем процесс входа пользователя, как было показано ранее
        client.initialize(this::handleUpdate, this::handleUpdateException, this::handleDefaultException)
        // переводим класс в свое внутреннее состояние, на самом деле можно без этого
        gotoNextState()
        // не дожидаясь окончания получения пользователя возвращаем "отложенный результат"
        return loginCompletableFuture
    }

    private suspend fun loadMe() {
        val response = sendAsFunction(TdApi.GetMe()).await()

        when (response) {
            is TdApi.User -> {
                // получили результата и передаем его ожидающим потокам
                loginCompletableFuture.complete(response)
                gotoNextState()
            }

            is TdApi.Error -> gotoNextState(TelegramError(response))
        }
    }
}
```

Вызов класса выглядит так:

```kotlin
val lvTelegramClient = LvTelegramClient(settings, loginPhone)
val user = lvTelegramClient.login() // получили ссылку на отложенный результат
           .await()                 // ждем (отпустив поток), когда результат будет получен
log.info { "logged as user: ${user.phoneNumber}, ${user.firstName} ${user.lastName}"  }
```

### Классы и сообщения из сценария
Сообщения из сценария
| Класс | Описание из документации |
| :--- | :--- |
| [getMe](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_me.html) | Returns the current user. Returns `object_ptr<user>`. |
| [user](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1user.html) | Represents a user. |

Классы из сценария
| Класс | Описание из документации |
| :--- | :--- |
| [user](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1user.html) | Represents a user. |


## Получение и чатов и определение их типа

*Важно, что в данной статье акцент сделан на чтение из Telegram. Поэтому модель приближена именно к "читателю", а важные нюансы для "создателя" опущены*.

### Типы чатов и сообщений
![](https://habrastorage.org/webt/pe/6h/pf/pe6hpflshaqyu7ot-svebxe7mja.png)

В Telegram чат ([`chat`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1chat.html)) и сообщения ([`message`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1message.html)) это основные понятия. Каждое сообщение принадлежит одному чату, а в чате может быть много сообщений (или не быть ни одного). Каналы, группы - с точки зрения API Telegram это разные типы чатов.

Сообщения могут содержать различный контент (см. [`MessageContent`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_message_content.html)). Основной из них - [`messageText`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1message_text.html)

Чаты один-на-один (one-to-one) позволяют пользователям обмениваться между собой личными сообщениями, которые не доступны другим пользователя. Они делятся на обычные (private) и секретные (secret). Секретные чаты шифруют сообщения и в данной статье мы их рассматривать не будем.

Отдельно выделяются группы (Groups) - в этих чатах сообщения доступны многим пользователям:
* **Supergroup** - группа с большим количество пользователями (до 200 000).
* **Channel** - каналы, публиковать в которые  могут только админы и может иметь неограниченное количество подписчиков. К каналу могут быть привязаны:
	* **Discussion group** (**Linked chat**) - чат для обсуждения новостей в канале
	* **Direct message** - чат для возможности анонимной связи с администраторами канала (анонимными остаются администраторы).
* **Forum** - группа с топиками
* **BasicGroup** - устаревшее понятие, заменено на `Supergroup`
* **Gigagroup** - только админы могут писать сообщения, но по количествую пользователей сняты. Мое подозрение, что это устаревший тип на замену которому пришли каналы. Тем не менее встретить `gigagroup` возможно.

*Пояснение*. Формально, чаты делятся по другому типу. И то, что у меня называется группа это супер группа ([`supergroup`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1supergroup.html)) с разными типами. Но это скорее тяжелое наследие и моя иерархия лучше отражает происходящее.

Определения типа группы и чатов канала довольно не тривиальная задача, которая будет подробно рассмотрена ниже.

Важно упомнить понятие список чатов (chat list). Они могут быть:
* **mainList** - главный список
* **archive** - чаты в архиве
* **folder** - папки пользователя.

В коде можно таким образом зафиксировать полное описание чата, канала и форума:

```kotlin
sealed interface ChatInformation {
    val chat: TdApi.Chat
    fun info(): String = "${chat.id} ${chat.title}"
}

data class ChatInfo(
    override val chat: TdApi.Chat
) : ChatInformation

data class ChannelInfo(
    override val chat: TdApi.Chat,
    val supergroup: SupergroupInfo,
    val discussionsChat: TdApi.Chat?,
    val directMessagesChat: TdApi.Chat?
) : ChatInformation

data class GroupInfo(
    override val chat: TdApi.Chat,
    val supergroupInfo: SupergroupInfo
) : ChatInformation

data class ForumInfo(
    override val chat: TdApi.Chat,
    private val topicsById: Map<ForumTopicId, TdApi.ForumTopic>
) : ChatInformation {
    val topics : List<TdApi.ForumTopic> = topicsById.values.toList().sortedBy { -it.order }          // must be sorted by the order in descending order

    fun findTopic(id : Long): TdApi.ForumTopic? = topicsById[id]

    companion object {
        fun from(chat: TdApi.Chat, topics: Collection<TdApi.ForumTopic>) =
            ForumInfo(chat, topics.associateBy { it.info.forumTopicId })
    }

}
```

Для полноты картины необходимо упомянуть [монофорумы](https://core.telegram.org/api/monoforum) (monoforum). Это механизм реализации анонимной связи с администратором (direct messages). Пользователи (не администраторы) видят только свои сообщения, а администраторы видят все диалоги в виде топиков (один пользователь - один топик).

Подробное описание групп есть в документации: [Channels, supergroups, gigagroups and basic groups](https://core.telegram.org/api/channel).

### Получение чатов

![](https://habrastorage.org/webt/rz/d7/eh/rzd7ehkdhyb0frmzkizh-hj6xa0.png)

Диаграмма  показывает взаимодействия клиента (что нужно послать серверу) и сообщения от сервера. [`LoadChats`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1load_chats.html) принимает на вход список чатов (chat list) и количество загружаемых чатов.  В количестве можно указывать разные значение, но TDLib оставляет за собой право использовать свое "для оптимизации работы". Если события по всем чатам переданы, функция возвращает ошибку 404. Далее вызывать её не нужно.

Таким образом, нам надо сделать два независимых потока обработки (thread) - поток посылки запросов и потока обработки сообщений. Это достаточно логичное решение, если помнить - информация о новом чате может придти из-за действий пользователя в другом запущенном приложении (например, он подписался на канал).

Для каждого чата сервер отправляет следующие сообщения:
* [`UpdateSupergroup`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_supergroup.html) - только для супер групп. Содержит только базовую информацию о супергруппе, поэтому надо самостоятельно запросить `supergroupFullInfo` (подробнее в сценариях определения типа группы).
* [`updateNewChat`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_new_chat.html) - информация о чате.
* [`updateChatAddedToList`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_chat_added_to_list.html) - информация, какому списку принадлежит чат.
* [`updateChatLastMessage`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_chat_last_message.html) - информация о последнем сообщение в чате. Можно использовать для вывода в боковой панели, а можно для определения необходимости загрузки истории чата.
* [`UpdateChatPosition`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_chat_position.html) - последнее сообщение из цепочки основных. Указывает на визуальный порядок чатов.


В моем примере код инициализации сообщений выглядит так. Я специально использую количество чатов 1, чтобы пройти цикл несколько раз и убедится, что все работает корректно.
```kotlin
suspend fun loadChats() {
    if (state != TelegramClientState.AUTHORIZED) {
        throw IllegalStateException("You should authorized before call ")
    }

    var hasMoreChats = true
    while (hasMoreChats) {
        val result = sendAsFunction(TdApi.LoadChats(TdApi.ChatListMain(), 1)).await()
        log.trace { "[response] loadChats result: $result" }
        hasMoreChats = when (result) {
            is TdApi.Error -> {
                if (result.code == 404) { // 404 describe "No more chats"
                    log.debug { "[response] LoadChats: 404, no more chats" }
                    false
                } else {
                    throw TelegramError(result)
                }
            }

            else -> {
                log.debug { "[response] LoadChats: Ok, should load more" }
                true
            }
        }
    }
}
```

Обработка сообщений вынесена в отдельный класс `ChatInformationComposer`, в котором они обрабатываются последовательно. Поскольку моя цель вывести типизированный список чатов, мне достаточно обрабатывать только два сообщения: [`UpdateSupergroup`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_supergroup.html) и [`UpdateNewChat`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_new_chat.html)

```kotlin
private fun handleUpdate(update: TdApi.Object) = coroutineScope.launch {
    logUpdate(update)
    when (update) {
        // Обработка сообщений авторизации
        is TdApi.UpdateAuthorizationState -> {
            authorizationChannel.send(update)
        }

        // Обработка сообщений загрузки чатов и определения их тпа
        is TdApi.UpdateSupergroup,
        is TdApi.UpdateNewChat -> {
            chats.processUpdate(update)
        }
    }
}
```

### Классы chat, superGroup и superGroupFullInfo

Сообщение [`updateNewChat`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_new_chat.html) содержит поле [`chat`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1chat.html) с информацией о чате. Оно содержит всю информацию, которая необходима для отображения чата в списке и работе с ним. Нас будет интересовать поля `id`, `title` и `type` типа [`ChatType`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_chat_type.html). `ChatType`представляет отдельную иерархию классов, каждый класс содержит свой набор полей.

Если у нас *не* чат один-на-один, то перед событием updateNewChat обязательно придет событие [`UpdateSupergroup`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_supergroup.html) c полем [`supergroup`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1supergroup.html). Проанализировав его поля можно определить, какой именно у чата тип:
* `is_channel` - чат является каналом
* `is_forum` - чат является форумом (группа с темами)
* `is_direct_messages_group` - если `is_channel == false`, чат является чатом для общения с администратором канала, иначе - канал имеет чат для общения
*  `has_linked_chat` - если `is_channel == false`, чат является чатом для обсуждения сообщения в канале, иначе канал имеет чат для обсуждения статьей

Есть важный нюанс - `supergroup` *не* содержит идентификатор чата, который она описывает. Связь обратная - чат имеет тип класса [`chatTypeSupergroup`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1chat_type_supergroup.html), в котором есть поле `supergroup_id`. Поэтому при получении `supergroup` его нужно сохранить в кэше.

Для канала `supergroup` содержит признаки наличия связанных чатов для общения или обсуждения. Но при  этом не содержит идентификаторы этих чатов. Для полученной полной информации о супергруппе нужно запросить [`supergroupFullInfo`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1supergroup_full_info.html). Для определения идентификаторов связанных чатов понадобятся поля этого класса:
* `linkedChatId` - идентификатор группы для обсуждения статей (или признак канал обсуждения)
* `direct_messages_chat_id` - идентификатор группы для общения чатов (или признак чата обсуждения).

Для этих идентификатор работает логика взаимного связывания:
* если `supergroup.is_channel == true`, то поля хранят id связанных с каналом чатом;
* если `supergroup.is_channel == false`, то чат является связанным с каналом и в поле указан id чата канала.

В коде эту информацию о типах можно зафиксировать таким образом:
```kotlin
enum class SupergroupType {
    Group,
    Channel,
    DiscussionChat,
    DirectMessageChat,
    Forum
}

// используем (!isChannel && xxx) для наглядности, т.к. в этих ветках isChannel всегда false
// из-за первой проверки
@Suppress("KotlinConstantConditions")
fun TdApi.Supergroup.calculateType(): SupergroupType {
    return when {
        isChannel -> SupergroupType.Channel
        isForum -> SupergroupType.Forum
        (!isChannel && hasLinkedChat) -> SupergroupType.DiscussionChat
        (!isChannel && isDirectMessagesGroup) -> SupergroupType.DirectMessageChat
        else -> SupergroupType.Group
    }
}

```


### Обработка события updateSupergroup

Как уже было отмечено выше, для реализации хранения структуры чатов пользователей нам будет необходимо обработать два сообщения: [`updateSupergroup`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_supergroup.html) и [`updateNewChat`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_new_chat.html). Так как информация о супергруппе будет использована в момент обработки события updateNewChat, обработка самого события `updateSupergroup` сводится к получению полной информации о супергруппе с помощью вызова функции Для получения полной информации о супергруппе, всегда будет выполнен запрос [`getSupergroupFullInfo`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_supergroup_full_info.html) и кэшированию этих данных для анализа в в обработчике `updateNewChat`.

```kotlin
class ChatInformationComposer(<...skipped...>) {
    // кэш для хранения полной информации о супергруппе
    private val supergroups = ConcurrentHashMap<SupergroupId, SupergroupInfo>()

    <...skipped...>

    private suspend fun processSupergroup(update: TdApi.UpdateSupergroup) {
        log.debug { "[process] ${update.shortInfo()}" }
        log.trace { "$update" }

        val supergroupId = update.supergroup.id
        val supergroup = update.supergroup
        val getSupergroupFullInfo = TdApi.GetSupergroupFullInfo(supergroupId)
        val response = client.sendAsFunction(getSupergroupFullInfo, log).await();

        if (response is TdApi.SupergroupFullInfo) {
            supergroups[supergroup.id] = SupergroupInfo(supergroup, response)
        } else {
            log.error { "Can't load supergroupFullInfo, supergroupId = ${supergroupId}, error = $response" }
            response.throwExceptionOnError()
        }
    }
    <...skipped...>
}
```

### Обработка события updateNewChat

**Определение чата один-на-один**

Самое интересное происходит в методе `processNewChat`, который обрабатывает сообщение `updateNewChat`. Начнем с определения самого простого сценария - чата один на один. Он однозначно
определяется по классу поля `type` из объекта `chat`.
```kotlin
class ChatInformationComposer(
    private val client: TelegramClient, private val coroutineScope: CoroutineScope)
{

    private val supergroups = ConcurrentHashMap<SupergroupId, SupergroupInfo>()

    private val chats = ConcurrentHashMap<ChatId, ChatInfo>()
    private val groups = ConcurrentHashMap<ChatId, GroupInfo>()
    private val channels = ConcurrentHashMap<ChatId, ChannelInfo>()
    private val forums = ConcurrentHashMap<ChatId, ForumInfo>()

    private suspend fun processNewChat(update: TdApi.UpdateNewChat) {
        val chat = update.chat;
        val chatId = chat.id
        when (val type = chat.type) {
            is TdApi.ChatTypeBasicGroup -> {/*deprecated type*/
            }
            is TdApi.ChatTypePrivate,
            is TdApi.ChatTypeSecret -> chats[chatId] = ChatInfo(chat)

        <...skipped...>
    }
}
```
**Определение группы**

При обработке поля `type` c классом `ChatTypeSupergroup` нам надо из кэша получить информацию о супергруппе (которую мы сохранили в функции `processSupergroup`) и делать анализ на ее основе. Ситуации, что пришел чат, а супергруппа по нему не пришла - не возможна, но все-таки она явно обработана. Самый простой вариант супегруппы - это просто группа.

```kotlin
class ChatInformationComposer {
  private val groups = ConcurrentHashMap<ChatId, GroupInfo>()

  private suspend fun processNewChat(update: TdApi.UpdateNewChat) {
        val chat = update.chat;
        val chatId = chat.id
        when (val type = chat.type) {

        <...skipped...>

            is TdApi.ChatTypeSupergroup -> {
                val supergroup = supergroups.get(type.supergroupId)
                if (supergroup != null) {
                    when (supergroup.type) {
                        SupergroupType.Group -> groups[chatId] = GroupInfo(chat, supergroup)

                        <...skipped...>

                    }
                } else {
                    log.error { "Unable find supergroup ${type.supergroupId}, chat.id = ${chat.id}, chat.title = ${chat.title}" }
                }
  }
}
```

**Обработка канала и связанных с ним чатов**

Канал полностью описывается тремя чатами:
* чат самого канала (обязательный), в котором администратор выкладывает посты и подписички их читают;
* чат обсуждения постов в канале (необязательный), в котором подписчики могут обсуждать посты;
* чат прямого общения с администратором канала (необязательный), при котором администратор остается анонимным.

*Примечание к реализации* В коде я полагаюсь на порядок сообщений - сначала приходит updateNewChat для канала, потом приходят связанные с ним чаты. Это сделано для наглядности обучающего примера, в реальном приложении  надо обрабатывать ситуацию прихода дополнительных чатов до (или без) чата канала через их кэширование.

При поступление информации о чате канала создается неизменяемая (immutable) структура `ChannelInfo` с незаполненными (`null`) полями `discussionsChat` и `directMessagesChat`. Сама структура сохраняется
в  HashMap и доступна по идентификатору чата. При поступление связанного с каналом происходит обновление информации. Чтобы не дублировать код обновления поля и замены значение в HashMap, я сделал вспомогательную функцию `updateChannel`.

```kotlin

<...skipped...>

data class ChannelInfo(
    override val chat: TdApi.Chat,
    val supergroup: SupergroupInfo,
    val discussionsChat: TdApi.Chat?,
    val directMessagesChat: TdApi.Chat?
) : ChatInformation

<...skipped...>

class ChatInformationComposer {
  private val groups = ConcurrentHashMap<ChatId, GroupInfo>()

  private val chats = ConcurrentHashMap<ChatId, ChatInfo>()

  private fun updateChannel(
      supergroup: SupergroupInfo,
      fieldNameForError: String,
      updateAction: (ChannelInfo) -> ChannelInfo
  ) {
      val channelChatId = supergroup.channelChatId
      val channelInfo = channels.get(channelChatId)
      requireNotNull(channelInfo) {
          "Unable find channel to update $fieldNameForError. Supergroup: ${supergroup.supergroup.shortInfo()}"
      }
      val nextChannelInfo = updateAction(channelInfo)
      channels[channelChatId] = nextChannelInfo
  }

  private suspend fun processNewChat(update: TdApi.UpdateNewChat) {
        val chat = update.chat;
        val chatId = chat.id
        when (val type = chat.type) {

        <...skipped...>

            is TdApi.ChatTypeSupergroup -> {
                val supergroup = supergroups.get(type.supergroupId)
                if (supergroup != null) {
                    when (supergroup.type) {

                        <...skipped...>

                        SupergroupType.Channel -> {
                            val channelInfo = ChannelInfo(
                                chat = chat,
                                supergroup = supergroup,
                                discussionsChat = null,
                                directMessagesChat = null
                            )
                            channels[chatId] = channelInfo
                        }

                        SupergroupType.DiscussionChat -> {
                            updateChannel(supergroup, "discussion chat") { channelInfo ->
                                channelInfo.copy(discussionsChat = chat)
                            }
                        }

                        SupergroupType.DirectMessageChat -> {
                            updateChannel(supergroup, "direct messages chat") { channelInfo ->
                                channelInfo.copy(directMessagesChat = chat)
                            }
                        }

                        <...skipped...>

                    }
                } else {
                    log.error { "Unable find supergroup ${type.supergroupId}, chat.id = ${chat.id}, chat.title = ${chat.title}" }
                }
  }
}
```

**Обработка форума**

Форум это группа, в которой администратор задал определенные топики. С точки зрения пользователя, один топик - отдельный чат, который можно отобразить на отдельной вкладке. С точки протокола форум
реализуется одним чатом - у каждого сообщения в таком чате есть идентификатор `topic_id`.

Для получения списка топиков нужно выполнить функцию [`getForumTopics`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_forum_topics.html) - в ответ на это сообщение сервер возвращает список топиков форума [`ForumTopics`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1forum_topics.html).  `GetForumTopics` это пример функции, возврающей большое количество объектов. Telegram используется свой подход для обработки больших списков, который мы рассмотрим на этом примере. В параметры функции (или поля объекта) и возвращаемом объекте есть поля (или подобные):
* `offset_date`
* `offset_message_id`
* `offset_message_thread_id`

В большинстве случае логика обработки выглядит так:
1. В первый вызов функции передаем 0
2. В последующие вызовы функций передаем значения, полученные в ответе.

Загружаем до тех пор, пока возвращаются нужные нам сущности (в данном случае - топики).

В моем примере получение списка форумов вынесено в отдельную функцию:
```kotlin
class ChatInformationComposer
 
 <...skipped...>

 private suspend fun loadForumTopics(chat: TdApi.Chat): List<TdApi.ForumTopic> {
        val chatId = chat.id

        val forumTopics = mutableListOf<TdApi.ForumTopic>()

        var mustLoadTopics = true;

        // делаем response с 0, которые станут начальными параметрами первого запроса
        var response = TdApi.ForumTopics(-1, emptyArray(), 0, 0, 0)

        // цикл загрузки
        while (mustLoadTopics) {

            // формируем запрос на основе параметров из ответа (для первого запроса будут нули)
            val getForumTopics = TdApi.GetForumTopics(
                chatId,
                null,
                response.nextOffsetDate,
                response.nextOffsetMessageId,
                response.nextOffsetMessageThreadId,
                1
            )
            val responseOrError = client.sendAsFunction(getForumTopics, log).await();
            if (responseOrError is TdApi.ForumTopics) {
                response = responseOrError
                val topics = response.topics
                forumTopics.addAll(topics);
                mustLoadTopics = topics.isNotEmpty()

            } else {
                log.error {
                    "Unable load forum topics for chat.id=${chatId}, chat.title ${chat.title}. " +
                            "Response: ${response.shortInfo()}"
                }
                mustLoadTopics = false
                response.throwExceptionOnError()
            }
        }

        return forumTopics
    }

  <...skipped...>  
}    
```

Обработка форума в функции `updateNewChat`:

```kotlin

<...skipped...>

data class ForumInfo(
    override val chat: TdApi.Chat,
    private val topicsById: Map<ForumTopicId, TdApi.ForumTopic>
) : ChatInformation {
    val topics : List<TdApi.ForumTopic> = topicsById.values.toList().sortedBy { -it.order }          // must be sorted by the order in descending order

    fun findTopic(id : Long): TdApi.ForumTopic? = topicsById[id]

    companion object {
        fun from(chat: TdApi.Chat, topics: Collection<TdApi.ForumTopic>) =
            ForumInfo(chat, topics.associateBy { it.info.forumTopicId })
    }

}

<...skipped...>

class ChatInformationComposer {
  private val groups = ConcurrentHashMap<ChatId, GroupInfo>()

  private val chats = ConcurrentHashMap<ChatId, ChatInfo>()

  private fun updateChannel(
      supergroup: SupergroupInfo,
      fieldNameForError: String,
      updateAction: (ChannelInfo) -> ChannelInfo
  ) {
      val channelChatId = supergroup.channelChatId
      val channelInfo = channels.get(channelChatId)
      requireNotNull(channelInfo) {
          "Unable find channel to update $fieldNameForError. Supergroup: ${supergroup.supergroup.shortInfo()}"
      }
      val nextChannelInfo = updateAction(channelInfo)
      channels[channelChatId] = nextChannelInfo
  }

  private suspend fun processNewChat(update: TdApi.UpdateNewChat) {
        val chat = update.chat;
        val chatId = chat.id
        when (val type = chat.type) {

        <...skipped...>

            is TdApi.ChatTypeSupergroup -> {
                val supergroup = supergroups.get(type.supergroupId)
                if (supergroup != null) {
                    when (supergroup.type) {

                        <...skipped...>

                        SupergroupType.Forum -> {
                            val topics = loadForumTopics(chat)
                            val forumInfo = ForumInfo.from(chat, topics)
                            forums[chatId] = forumInfo
                        }

                        <...skipped...>

                    }
                } else {
                    log.error { "Unable find supergroup ${type.supergroupId}, chat.id = ${chat.id}, chat.title = ${chat.title}" }
                }
  }
}
```

## Получение сообщений из чата
Для получения списка сообщений из чата используется функция `getChatHistory`, которая которая возвращает список сообщений чата. В чате может быть много сообщений, поэтому она использует тот же принцип работы, что и рассмотренная в предыдущем разделе функция `getForumTopics`.  Подробно рассматривать не буду, просто приведу код.

```kotlin
  suspend fun loadMessages(chatId: ChatId) : List<TdApi.Message> {
        var shouldGetMessages = true;
        var fromMessageId = 0L;

        val chatMessages = mutableListOf<TdApi.Message>()

        while (shouldGetMessages) {
            val getChatHistory = TdApi.GetChatHistory(chatId, fromMessageId, 0, 1, false);
            when (val response = sendAsFunction(getChatHistory).await()) {
                is TdApi.Messages -> {
                    val responseMessages = response.messages;
                    shouldGetMessages = if (responseMessages.isNotEmpty()) {
                        chatMessages.addAll(responseMessages)
                        fromMessageId = responseMessages.last()!!.id;
                        true
                    } else {
                        false
                    }
                }

                is TdApi.Error -> {
                    log.warn { "Can't load chat, error = [${response}]" }
                    shouldGetMessages = false
                }
            };

        }

        return chatMessages
    }
```
# Заключение

В статье рассмотрены основные сценарии, которые необходимы для чтения данных из Telegram, к которым есть доступ у пользователя. Чтение можно сделать незаметным для других участников общения. Понимание принципов протокола и чтения документации позволит реализовать любой сценарий, который будет необходим.
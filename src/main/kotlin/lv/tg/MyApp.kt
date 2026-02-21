package lv.tg

import io.github.oshai.kotlinlogging.KotlinLogging
import it.tdlight.Slf4JLogMessageHandler
import it.tdlight.client.APIToken
import it.tdlight.jni.TdApi
import it.tdlight.util.LibraryVersion
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import lv.tg.client.LvTelegramClient
import lv.tg.client.shortInfo
import java.util.*
import kotlin.io.path.Path
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) = runBlocking {
    val log = KotlinLogging.logger {  }
    it.tdlight.Init.init()
    it.tdlight.Log.setLogMessageHandler(2, Slf4JLogMessageHandler())
    val apiToken = APIToken.example();
    val sessionPath = Path("sessions","example2")


    val settings = TdApi.SetTdlibParameters().apply {
        useTestDc = true
        apiId = apiToken.apiID
        apiHash = apiToken.apiHash

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

    val loginPhone = args[0]
    if (loginPhone.isNullOrBlank()) {
        println("Provide user's phone number as application argument in the international format")
        exitProcess(1)
    }

    val lvTelegramClient = LvTelegramClient(settings, loginPhone)
    val user = lvTelegramClient.login().await()
    log.info { "logged as user: ${user.phoneNumber}, ${user.firstName} ${user.lastName}"  }
    lvTelegramClient.loadChats();
    println("chat loading...")
    delay(1.seconds)

    val chatsInfo = StringBuilder()
    chatsInfo.append("============= Chats ==========\n");
    for (chat in lvTelegramClient.chats()) {
        chatsInfo.append("\t${chat.info()}\n")
    }

    chatsInfo.append("============ Channels ============\n")
    for (channel in lvTelegramClient.channels()) {
        chatsInfo.append("\t${channel.info()}\n")
        if (channel.discussionsChat != null) {
            chatsInfo.append("\t\t | ${channel.discussionsChat!!.shortInfo()}\n")
        }

        if (channel.directMessagesChat != null) {
            chatsInfo.append("\t\t | ${channel.directMessagesChat!!.shortInfo()}\n")
        }
    }

    chatsInfo.append("=========== Groups ===========\n")
    for (chat in lvTelegramClient.groups()) {
        chatsInfo.append("\t${chat.info()}\n")
    }

    chatsInfo.append("========== Forums ============\n")
    for (forum in lvTelegramClient.forums()) {
        chatsInfo.append("\t${forum.info()}\n")
        for (topic in forum.topics) {
            chatsInfo.append("\t\t| ${topic.shortInfo()}\n")
        }
    }

    println(chatsInfo)


    val messages = lvTelegramClient.loadMessages(5002560862);
    println ("\n\n================ Messages from ${5002560862} ================")

    messages.forEach { msg ->
        val content = when (val c = msg.content) {
            is TdApi.MessageText -> c.text.text;
            else -> c::class.simpleName

        }
        println("${msg.id} \t ${msg.date} \t ${content}")
    }

    exitProcess(0);
    Unit
}
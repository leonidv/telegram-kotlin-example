package lv.tg

import it.tdlight.Init
import it.tdlight.Log
import it.tdlight.Slf4JLogMessageHandler
import it.tdlight.client.*
import it.tdlight.jni.TdApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant.now
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.system.exitProcess


suspend fun main() {
    Init.init()
    Log.setLogMessageHandler(3, Slf4JLogMessageHandler())
    val factory = SimpleTelegramClientFactory()
    val apiToken = APIToken.example();
    val settings = TDLibSettings.create(apiToken)
    val sessionPath = Path("sessions","example")
    println(sessionPath.absolutePathString())
    settings.databaseDirectoryPath = sessionPath.resolve("data")
    settings.downloadedFilesDirectoryPath = sessionPath.resolve("downloads")

    val clientBuilder = factory.builder(settings)
    val authenticationData = AuthenticationSupplier.user("+79667580309")
    settings.setUseTestDatacenter(true)

    ExampleApp().use { app ->
        app.init(authenticationData, clientBuilder)
        val user = app.user()
        println("Logged as: [${user.id}]  ${user.firstName} ${user.lastName} \n")

//        val chats = app.loadChats()
//        chats.forEach { println("${it.id}\t${it.title}") }

        coroutineScope {
            this.launch {
                while (!app.mustExit().also { println("must exit: $it") }) {
                    delay(1000)
                    val response = app.sentTextMessage(app.user().id,"Hello, Telegram! ${now()}").await()
                    println("Sent message\n")
                }
            }
        }
        println("before close")
    }
    exitProcess(0)
}


package lv.tg.client

import io.github.oshai.kotlinlogging.KLogger
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

/**
 * Send query to TDLib. Throw Telegram exception on error
 * and [exceptionHandler] as exception handler
 */
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
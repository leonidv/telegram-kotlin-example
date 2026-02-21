package lv.tg.client

import io.github.oshai.kotlinlogging.KotlinLogging
import it.tdlight.ClientFactory
import it.tdlight.Init
import it.tdlight.TelegramClient
import it.tdlight.client.TelegramError
import it.tdlight.jni.TdApi
import it.tdlight.util.UnsupportedNativeLibraryException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.io.path.Path

/**
 * State of telegram client. Order is used to show order of states.
 */
enum class TelegramClientState(@Suppress("unused") order: Int) {
    /**
     * Client just created and can accept and send messaged to TDLib,
     * but only small portion of API are available (see [Telegram Documentation](https://core.telegram.org/api/auth#we-are-authorized)).
     *
     */
    CLIENT_CREATED(0),

    /**
     * Authorization in progress. Application in the one of [AuthorizationState](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_authorization_state.html)
     * but still not yet in the [AuthorizationStateReady](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1authorization_state_ready.html)
     *
     * User also is not loaded.
     */
    AUTHORIZATION(1),

    /**
     * Authorization is completed and User is loaded via [getMe](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_me.html).
     *
     * [LvTelegramClient.login] is completed.
     *
     */
    AUTHORIZED(2),


    /**
     * Authorization or loading user is failed.
     */
    AUTHORIZATION_ERROR(-1)
}

class LvTelegramClient(
    private val settings: TdApi.SetTdlibParameters,
    private val userPhoneNumber: String
) : AutoCloseable {
    companion object {
        init {
            try {
                Init.init()
            } catch (_: UnsupportedNativeLibraryException) {
                throw RuntimeException("Can't load native libraries")
            }
        }

        val log = KotlinLogging.logger { }

        const val LOG_UPDATE_OPTIONS = false
        const val LOG_CONNECTION_STATE = false
    }

    var state: TelegramClientState

    val client: TelegramClient

    val authorizationChannel: Channel<TdApi.UpdateAuthorizationState> = Channel()

    val coroutineContext = newFixedThreadPoolContext(1, "Telegram client")
    val coroutineScope = CoroutineScope(coroutineContext)

    val loginCompletableFuture = CompletableFuture<TdApi.User>()

    private val chats: ChatInformationComposer

    init {
        createDirectories(settings)

        coroutineScope.launch {
            for (authorizationUpdate in authorizationChannel) {
                processAuthorization(authorizationUpdate)
            }
        }


        val clientFactory = ClientFactory.create()
        client = clientFactory.createClient()
        chats = ChatInformationComposer(this.client, this.coroutineScope)

        state = TelegramClientState.CLIENT_CREATED

    }

    fun login(): CompletableFuture<TdApi.User> {
        client.initialize(this::handleUpdate, this::handleUpdateException, this::handleDefaultException)
        gotoNextState()
        return loginCompletableFuture
    }

    fun user(): TdApi.User {
        if (state != TelegramClientState.AUTHORIZED) {
            throw IllegalStateException("You should login before read user")
        }

        return loginCompletableFuture.get()!!
    }

    private fun gotoNextState(error: Throwable? = null) {
        when (state) {
            TelegramClientState.CLIENT_CREATED -> {
                this.state = TelegramClientState.AUTHORIZATION
            }

            TelegramClientState.AUTHORIZATION -> {
                if (error == null) {
                    this.state = TelegramClientState.AUTHORIZED
                } else {
                    this.state = TelegramClientState.AUTHORIZATION_ERROR
                    this.loginCompletableFuture.completeExceptionally(error)
                }
            }

            TelegramClientState.AUTHORIZED -> TODO()
            TelegramClientState.AUTHORIZATION_ERROR -> TODO()
        }
    }

    fun sendAsProcedure(query: TdApi.Function<*>) {
        client.sendAsProcedure(query, this::handleUpdateException, log)
    }

    fun sendAsFunction(query: TdApi.Function<*>): CompletableFuture<TdApi.Object> {
        return client.sendAsFunction(query, log)
    }

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

    private fun createDirectories(settings: TdApi.SetTdlibParameters) {
        try {
            listOf(settings.databaseDirectory, settings.filesDirectory)
                .map { Path(it).toAbsolutePath() }
                .forEach {
                    if (Files.notExists(it)) {
                        Files.createDirectories(it)
                    }
                }
        } catch (ex: IOException) {
            throw UncheckedIOException("Can't create tdlib directories", ex)
        }
    }

    @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
    private fun logUpdate(update: TdApi.Object) {
        if (!LOG_UPDATE_OPTIONS && update.isParametersOrOption()) {
            return
        }

        if (!LOG_CONNECTION_STATE && update is TdApi.UpdateConnectionState) {
            return
        }

        if (log.isDebugEnabled()) {
            log.debug { "[update] ${update.shortInfo()}" }
            log.trace { "$update" }
        }

    }

    private fun handleUpdate(update: TdApi.Object) = coroutineScope.launch {
        logUpdate(update)
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                authorizationChannel.send(update)
            }

            is TdApi.UpdateSupergroup,
            is TdApi.UpdateNewChat -> {
                chats.processUpdate(update)
            }
        }
    }

    private fun handleUpdateException(ex: Throwable) = runBlocking {
        log.debug { "[handle exception] ${ex::class.simpleName}" }
        log.trace(ex) {}
    }

    private fun handleDefaultException(ex: Throwable) = runBlocking {
        log.debug { "[default exception] ${ex::class.simpleName}" }
        log.trace(ex) {}
    }

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

    /**
     * Returns all user's chats without classification.
     */
    fun allChats() = chats.getAllChats()

    /**
     * Returns secrets and privates user's chats
     */
    fun chats(): List<ChatInfo> = chats.getChats()

    /**
     * Returns a list of channels the user is subscribed to.
     */
    fun channels(): List<ChannelInfo> = chats.getChannels()

    /**
     * Returns a list of groups the user is a member of.
     */
    fun groups(): List<GroupInfo> = chats.getGroups()

    /**
     * Return list of forums the is user a member of.
     */
    fun forums(): List<ForumInfo> = chats.getForums()


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

    override fun close() {
        authorizationChannel.close()
        coroutineContext.close()
    }

}


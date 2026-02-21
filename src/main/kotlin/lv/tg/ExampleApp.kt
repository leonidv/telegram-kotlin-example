package lv.tg

import io.github.oshai.kotlinlogging.KotlinLogging
import it.tdlight.client.SimpleAuthenticationSupplier
import it.tdlight.client.SimpleTelegramClient
import it.tdlight.client.SimpleTelegramClientBuilder
import it.tdlight.jni.TdApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

class ExampleApp() : AutoCloseable {
    private val log = KotlinLogging.logger {  }
    companion object {
        val MAX_CHAT_REQUESTS = 100
    }

    private var client: SimpleTelegramClient? = null

    private var user: TdApi.User? = null

    private var mustExit: Boolean = false;

    private val chats: MutableList<TdApi.Chat> = mutableListOf()

    private val newChatUpdatesChannel = Channel<TdApi.UpdateNewChat>()

    private fun buildClient(
        authenticationData: SimpleAuthenticationSupplier<*>,
        clientBuilder: SimpleTelegramClientBuilder
    ): SimpleTelegramClient {
        clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState::class.java, this::onUpdateAuthorizationState)
        clientBuilder.addUpdateHandler(TdApi.UpdateNewMessage::class.java, this::onUpdateNewMessage)
        clientBuilder.addUpdateHandler(TdApi.UpdateNewChat::class.java) { update ->
            runBlocking {
                newChatUpdatesChannel.send(
                    update
                )
            }
        }

        clientBuilder.addCommandHandler<TdApi.UpdateNewMessage>("stop", this::onStopCommand)

        return clientBuilder.build(authenticationData)!!
    }

    suspend fun init(authenticationData: SimpleAuthenticationSupplier<*>, clientBuilder: SimpleTelegramClientBuilder) =
        coroutineScope {
            client = buildClient(authenticationData, clientBuilder)
            user = client().meAsync.await()

            launch {
                while (!client().isMainChatsListLoaded) {
                    delay(25)
                }
            }.join()
            log.debug { "Main chats loaded" }

            launch() {
                for (chat in newChatUpdatesChannel) {
                    onNewChatUpdate(chat)
                }
            }

        }

    fun onNewChatUpdate(updateNewChat: TdApi.UpdateNewChat) {
        val chat = updateNewChat.chat!!
        val type = chat.type
        val chatTypeInfo = when(type) {
            is TdApi.ChatTypeBasicGroup -> "Basic group"
            is TdApi.ChatTypePrivate -> "Private with user: ${type.userId}"
            is TdApi.ChatTypeSecret -> "Secret with user: ${type.userId}"
            is TdApi.ChatTypeSupergroup ->"Supergroup: supergroupId = ${type.supergroupId}, is_channel = ${type.isChannel}"
            else -> "unknown type"
        }

        println("[event] ${updateNewChat::class.simpleName} \tid = ${chat.id}, title = ${chat.title}; $chatTypeInfo")
        chats.add(updateNewChat.chat!!)

        if (type is TdApi.ChatTypeSupergroup) {
            client().send(TdApi.GetSupergroupFullInfo(chat.id))
        }
    }

    fun client(): SimpleTelegramClient {
        if (this.client == null) {
            throw IllegalStateException("call init before use client")
        }

        return client!!
    }

    fun user(): TdApi.User {
        if (this.user == null) {
            throw IllegalStateException("call init before use user")
        }

        return user!!;
    }

    fun mustExit(): Boolean = this.mustExit;

    fun sentTextMessage(chatId: Long, text: String): CompletableFuture<TdApi.Message> {
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = TdApi.InputMessageText().apply {
                this.text = TdApi.FormattedText(text, arrayOfNulls(0))
            }
        }

        return client().sendMessage(request, true)
    }

    override fun close() {
        client?.let { it.close() }
        client = null
        user = null
        newChatUpdatesChannel.close()
        println("Client is closed")
    }

    private fun onUpdateAuthorizationState(update: TdApi.UpdateAuthorizationState) {
        print("authorization state update: ")
        when (update.authorizationState) {
            is TdApi.AuthorizationStateReady -> println("User is logged in, do something interesting")
            is TdApi.AuthorizationStateClosing -> println("Closing...")
            is TdApi.AuthorizationStateClosed -> println("Closed")
            is TdApi.AuthorizationStateLoggingOut -> println("Logging out...")
            else -> {
                println(update.authorizationState::class.simpleName)
            }
            //<editor-fold desc="all another states">
//            is TdApi.AuthorizationStateWaitCode -> TODO()
//            is TdApi.AuthorizationStateWaitEmailAddress -> TODO()
//            is TdApi.AuthorizationStateWaitEmailCode -> TODO()
//            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> TODO()
//            is TdApi.AuthorizationStateWaitPassword -> TODO()
//            is TdApi.AuthorizationStateWaitPhoneNumber -> TODO()
//            is TdApi.AuthorizationStateWaitPremiumPurchase -> TODO()
//            is TdApi.AuthorizationStateWaitRegistration -> TODO()
//            is TdApi.AuthorizationStateWaitTdlibParameters -> TODO()
//</editor-fold>
        }
    }

    private fun onUpdateNewMessage(update: TdApi.UpdateNewMessage) {
        println("new message update [${update::class.simpleName}]")
        val content = update.message.content;
        //update.message.senderId
        val messageText = when (content) {
            is TdApi.MessageText -> content.text.text
            else -> content::class.simpleName
            //<editor-fold desc="all another message types">
//            is TdApi.MessageAnimatedEmoji -> TODO()
//            is TdApi.MessageAnimation -> TODO()
//            is TdApi.MessageAudio -> TODO()
//            is TdApi.MessageBasicGroupChatCreate -> TODO()
//            is TdApi.MessageBotWriteAccessAllowed -> TODO()
//            is TdApi.MessageCall -> TODO()
//            is TdApi.MessageChatAddMembers -> TODO()
//            is TdApi.MessageChatBoost -> TODO()
//            is TdApi.MessageChatChangePhoto -> TODO()
//            is TdApi.MessageChatChangeTitle -> TODO()
//            is TdApi.MessageChatDeleteMember -> TODO()
//            is TdApi.MessageChatDeletePhoto -> TODO()
//            is TdApi.MessageChatJoinByLink -> TODO()
//            is TdApi.MessageChatJoinByRequest -> TODO()
//            is TdApi.MessageChatSetBackground -> TODO()
//            is TdApi.MessageChatSetMessageAutoDeleteTime -> TODO()
//            is TdApi.MessageChatSetTheme -> TODO()
//            is TdApi.MessageChatShared -> TODO()
//            is TdApi.MessageChatUpgradeFrom -> TODO()
//            is TdApi.MessageChatUpgradeTo -> TODO()
//            is TdApi.MessageChecklist -> TODO()
//            is TdApi.MessageChecklistTasksAdded -> TODO()
//            is TdApi.MessageChecklistTasksDone -> TODO()
//            is TdApi.MessageContact -> TODO()
//            is TdApi.MessageContactRegistered -> TODO()
//            is TdApi.MessageCustomServiceAction -> TODO()
//            is TdApi.MessageDice -> TODO()
//            is TdApi.MessageDirectMessagePriceChanged -> TODO()
//            is TdApi.MessageDocument -> TODO()
//            is TdApi.MessageExpiredPhoto -> TODO()
//            is TdApi.MessageExpiredVideo -> TODO()
//            is TdApi.MessageExpiredVideoNote -> TODO()
//            is TdApi.MessageExpiredVoiceNote -> TODO()
//            is TdApi.MessageForumTopicCreated -> TODO()
//            is TdApi.MessageForumTopicEdited -> TODO()
//            is TdApi.MessageForumTopicIsClosedToggled -> TODO()
//            is TdApi.MessageForumTopicIsHiddenToggled -> TODO()
//            is TdApi.MessageGame -> TODO()
//            is TdApi.MessageGameScore -> TODO()
//            is TdApi.MessageGift -> TODO()
//            is TdApi.MessageGiftedPremium -> TODO()
//            is TdApi.MessageGiftedStars -> TODO()
//            is TdApi.MessageGiftedTon -> TODO()
//            is TdApi.MessageGiveaway -> TODO()
//            is TdApi.MessageGiveawayCompleted -> TODO()
//            is TdApi.MessageGiveawayCreated -> TODO()
//            is TdApi.MessageGiveawayPrizeStars -> TODO()
//            is TdApi.MessageGiveawayWinners -> TODO()
//            is TdApi.MessageGroupCall -> TODO()
//            is TdApi.MessageInviteVideoChatParticipants -> TODO()
//            is TdApi.MessageInvoice -> TODO()
//            is TdApi.MessageLocation -> TODO()
//            is TdApi.MessagePaidMedia -> TODO()
//            is TdApi.MessagePaidMessagePriceChanged -> TODO()
//            is TdApi.MessagePaidMessagesRefunded -> TODO()
//            is TdApi.MessagePassportDataReceived -> TODO()
//            is TdApi.MessagePassportDataSent -> TODO()
//            is TdApi.MessagePaymentRefunded -> TODO()
//            is TdApi.MessagePaymentSuccessful -> TODO()
//            is TdApi.MessagePaymentSuccessfulBot -> TODO()
//            is TdApi.MessagePhoto -> TODO()
//            is TdApi.MessagePinMessage -> TODO()
//            is TdApi.MessagePoll -> TODO()
//            is TdApi.MessagePremiumGiftCode -> TODO()
//            is TdApi.MessageProximityAlertTriggered -> TODO()
//            is TdApi.MessageRefundedUpgradedGift -> TODO()
//            is TdApi.MessageScreenshotTaken -> TODO()
//            is TdApi.MessageSticker -> TODO()
//            is TdApi.MessageStory -> TODO()
//            is TdApi.MessageSuggestProfilePhoto -> TODO()
//            is TdApi.MessageSuggestedPostApprovalFailed -> TODO()
//            is TdApi.MessageSuggestedPostApproved -> TODO()
//            is TdApi.MessageSuggestedPostDeclined -> TODO()
//            is TdApi.MessageSuggestedPostPaid -> TODO()
//            is TdApi.MessageSuggestedPostRefunded -> TODO()
//            is TdApi.MessageSupergroupChatCreate -> TODO()
//            is TdApi.MessageUnsupported -> TODO()
//            is TdApi.MessageUpgradedGift -> TODO()
//            is TdApi.MessageUsersShared -> TODO()
//            is TdApi.MessageVenue -> TODO()
//            is TdApi.MessageVideo -> TODO()
//            is TdApi.MessageVideoChatEnded -> TODO()
//            is TdApi.MessageVideoChatScheduled -> TODO()
//            is TdApi.MessageVideoChatStarted -> TODO()
//            is TdApi.MessageVideoNote -> TODO()
//            is TdApi.MessageVoiceNote -> TODO()
//            is TdApi.MessageWebAppDataReceived -> TODO()
//            is TdApi.MessageWebAppDataSent -> TODO()
            //</editor-fold>
        }!!

        if (messageText == "stop") {
            println("Message 'stop' - must exit")
            this.mustExit = true;
        }
    }

    suspend fun loadChats(attempts : Int = 0): List<TdApi.Chat> {
        println("loadChats attempts=$attempts")
        if (attempts > MAX_CHAT_REQUESTS) return emptyList()

        try {
            val mainChatList = TdApi.ChatListMain()
            client().send(TdApi.LoadChats(mainChatList, 100))
            loadChats(attempts+1)
        } catch (e : Throwable) {
            println("finish loading chats? ${e::class.simpleName}")
            e.printStackTrace()
        }

        return this.chats
    }

    private fun onStopCommand(chat: TdApi.Chat, commandSender: TdApi.MessageSender, argumnets: String) {
        val isAdmin = when (commandSender) {
            is TdApi.MessageSenderUser -> commandSender.userId == this.user().id
            else -> false
        }

        if (isAdmin) {
            println("Recieved stop command. closing...")
            client().sendClose()
        }
    }
}
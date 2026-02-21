package lv.tg.client

import io.github.oshai.kotlinlogging.KotlinLogging
import it.tdlight.TelegramClient
import it.tdlight.jni.TdApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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

data class SupergroupInfo(
    val supergroup: TdApi.Supergroup,
    val fullInfo: TdApi.SupergroupFullInfo
) {
    val id = supergroup.id
    val isChannel = supergroup.isChannel
    val type = supergroup.calculateType()
    val channelChatId: ChatId = when (type) {
        SupergroupType.Group,
        SupergroupType.Forum,
        SupergroupType.Channel -> 0

        SupergroupType.DiscussionChat -> fullInfo.linkedChatId
        SupergroupType.DirectMessageChat -> fullInfo.directMessagesChatId
    }

    init {
        require(!isChannel || (isChannel && channelChatId == 0L)) { "channel can't has link to channelChatId, supergroup: ${supergroup.shortInfo()}" }
    }

}

enum class SupergroupType {
    Group,
    Channel,
    DiscussionChat,
    DirectMessageChat,
    Forum
}

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

class ChatInformationComposer(private val client: TelegramClient, private val coroutineScope: CoroutineScope) {
    private val log = KotlinLogging.logger { }

    private val supergroups = ConcurrentHashMap<SupergroupId, SupergroupInfo>()

    private val chats = ConcurrentHashMap<ChatId, ChatInfo>()
    private val channels = ConcurrentHashMap<ChatId, ChannelInfo>()
    private val groups = ConcurrentHashMap<ChatId, GroupInfo>()
    private val forums = ConcurrentHashMap<ChatId, ForumInfo>()

    private val updateChannel = Channel<TdApi.Object>()

    init {
        coroutineScope.launch {
            for (update in updateChannel) process(update)
        }
    }

    /**
     * You can send any [TdApi.Object], but method processes only:
     *  * [TdApi.UpdateSupergroup]
     *  * [TdApi.UpdateNewChat]
     *
     *  All another updates are ignored.
     */
    suspend fun processUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateSupergroup,
            is TdApi.UpdateNewChat -> updateChannel.send(update)
            else -> log.warn { "ChatInformationComposer does not process [${update::class.simpleName}" }
        }
    }


    private suspend fun process(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateSupergroup -> processSupergroup(update)
            is TdApi.UpdateNewChat -> processNewChat(update)
        }
    }

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
        log.debug { "[process] ${update.shortInfo()}" }
        log.trace { "$update" }

        val chat = update.chat;
        val chatId = chat.id
        when (val type = chat.type) {
            is TdApi.ChatTypeBasicGroup -> {/*deprecated type*/
            }

            is TdApi.ChatTypePrivate,
            is TdApi.ChatTypeSecret -> chats[chatId] = ChatInfo(chat)

            is TdApi.ChatTypeSupergroup -> {
                val supergroup = supergroups.get(type.supergroupId)

                if (supergroup != null) {
                    when (supergroup.type) {
                        SupergroupType.Group -> groups[chatId] = GroupInfo(chat, supergroup)
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

                        SupergroupType.Forum -> {
                            val topics = loadForumTopics(chat)
                            val forumInfo = ForumInfo.from(chat, topics)
                            forums[chatId] = forumInfo
                        }
                    }
                } else {
                    log.error { "Unable find supergroup ${type.supergroupId}, chat.id = ${chat.id}, chat.title = ${chat.title}" }
                }
            }
        }
    }

    private suspend fun loadForumTopics(chat: TdApi.Chat): List<TdApi.ForumTopic> {
        val chatId = chat.id

        val forumTopics = mutableListOf<TdApi.ForumTopic>()

        var mustLoadTopics = true;
        var response = TdApi.ForumTopics(-1, emptyArray(), 0, 0, 0)

        while (mustLoadTopics) {
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


    fun getAllChats(): List<ChatInformation> {
        return (channels.values + chats.values + groups.values + forums.values)
    }

    fun getChats(): List<ChatInfo> = chats.values.toList()

    fun getChannels(): List<ChannelInfo> = channels.values.toList()

    fun getGroups(): List<GroupInfo> = groups.values.toList()

    fun getForums(): List<ForumInfo> = forums.values.toList()
}
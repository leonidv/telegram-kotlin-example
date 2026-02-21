package lv.tg.client

import it.tdlight.client.TelegramError
import it.tdlight.jni.TdApi

/**
 * Log and throw [TelegramError] if Object is [TdApi.Error]
 */
fun TdApi.Object.throwExceptionOnError() {
    if (this is TdApi.Error) {
        val log = LvTelegramClient.log
        log.error { "$this" }
        throw TelegramError(this)
    }
}

val paramsKeywords = setOf("color", "emoji", "theme", "background", "animation", "effects", "settings")

fun TdApi.Object.isParametersOrOption(): Boolean {
    return when (this) {
        !is TdApi.Update -> false
        is TdApi.UpdateOption -> true
        is TdApi.UpdateSuggestedActions -> true
        is TdApi.UpdateAttachmentMenuBots -> true
        else -> {
            val className = this::class.simpleName.toString().lowercase()
            paramsKeywords.find { className.contains(it) } != null
        }
    }
}

/**
 * For id use "entity.id" instead of "entityId" notation for quick searching in the log.
 */
fun TdApi.Object.shortInfo() : String {
    val tdObject = this
    val extraInfo = when (tdObject) {
        is TdApi.UpdateNewChat -> tdObject.chat.shortInfo()

        is TdApi.UpdateChatPosition -> " chat.id = ${chatId}"
        is TdApi.UpdateChatLastMessage -> " chat.id = ${chatId}"
        is TdApi.UpdateChatAddedToList -> " chat.id = ${chatId}"
        is TdApi.UpdateSupergroup -> tdObject.supergroup.shortInfo()
        is TdApi.UpdateSupergroupFullInfo -> " ${supergroupId} ${supergroupFullInfo.shortInfo()}"

        is TdApi.Supergroup -> " supergroup.id = ${id}, ${usernames?.activeUsernames?.first()}"
        is TdApi.SupergroupFullInfo -> " supergroup.inviteLink = ${inviteLink}, " +
                "supergroup.linkedChatId = ${linkedChatId}, " +
                "supergroup.directMessagesChatId = ${directMessagesChatId}"
        is TdApi.Chat -> {
            val chat = tdObject
            val type = chat.type
            val typeInfo = when (type) {
                is TdApi.ChatTypeSupergroup -> {
                    "supergroup.id = ${type.supergroupId}"
                }

                is TdApi.ChatTypePrivate -> "private user.id = ${type.userId}"
                is TdApi.ChatTypeSecret ->  "secret user.id = ${type.userId}"
                is TdApi.ChatTypeBasicGroup -> "basic basicgroup.id = ${type.basicGroupId}"
                else -> ""
            }
            " chat.id = ${chat.id} ${typeInfo} ${title}"
        }

        is TdApi.ForumTopics -> " totalCount = ${totalCount}, topics.size = ${topics.size}"
        is TdApi.ForumTopic -> " ${info.shortInfo()}, order = ${order}"
        is TdApi.ForumTopicInfo -> " name = ${name}, chat.id = ${chatId}, forumTopic.id = ${forumTopicId}"
        else -> ""
    }

    return "${tdObject::class.simpleName}$extraInfo"
}
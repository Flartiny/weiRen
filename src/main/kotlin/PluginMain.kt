package org.example.mirai.plugin

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.info
import org.example.mirai.plugin.command.weiRenCommand
import org.example.mirai.plugin.data.weiRenConfig
import org.example.mirai.plugin.data.weiRenData
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 使用 kotlin 版请把
 * `src/main/resources/META-INF.services/net.mamoe.mirai.console.plugin.jvm.JvmPlugin`
 * 文件内容改成 `org.example.mirai.plugin.PluginMain` 也就是当前主类全类名
 *
 * 使用 kotlin 可以把 java 源集删除不会对项目有影响
 *
 * 在 `settings.gradle.kts` 里改构建的插件名称、依赖库和插件版本
 *
 * 在该示例下的 [JvmPluginDescription] 修改插件名称，id和版本，etc
 *
 * 可以使用 `src/test/kotlin/RunMirai.kt` 在 ide 里直接调试，
 * 不用复制到 mirai-console-loader 或其他启动器中调试
 */

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "org.weiren.mirai",
        name = "weiRen",
        version = "0.1.0"
    ) {
        author("Flartiny")
    }
) {
    private lateinit var myBot: Bot

    override fun onEnable() {
        weiRenConfig.reload()
        weiRenData.reload()
        weiRenCommand.register()

        val eventChannel = GlobalEventChannel.parentScope(this)
        eventChannel.subscribeAlways<GroupMessageEvent> {
            val groupId = group.id
            // 获取第一个为纯文本的成分，过滤md消息
            val messageContent = message.firstOrNull { it is PlainText }?.contentToString()?.filter { it.isISOControl().not() }
//            message.forEach { logger.info { "${it}" } }
//            message.forEach { logger.info { it.content } }
//            message.forEach { logger.info { "${it is PlainText}" } }
//            logger.info { "weiren get PlainText : ${this.message.any { it is PlainText }}" }
            if (!messageContent.isNullOrEmpty() && messageContent.length < weiRenConfig.maxMessageLength) {
                // 根据消息内容进行回复
                if (Random.nextDouble() < weiRenConfig.probability) {
                    val reply = generateReply(groupId, messageContent)
                    if (reply != null) {
                        logger.info { "matched reply" }
                        val delayTime = Random.nextLong(10_000, 30_000)
                        GlobalScope.launch {
                            delay(delayTime)
                            group.sendMessage(PlainText(reply))
                        }
                    }else {
                        // 没有匹配到回复，从词库中随机选择一句发送
                        logger.info { "random reply" }
                        val randomMessage = selectRandomMessage(groupId)
                        val delayTime = Random.nextLong(10_000, 30_000)
                        GlobalScope.launch {
                            delay(delayTime)
                            if (randomMessage != null) {
                                group.sendMessage(PlainText(randomMessage))
                            }
                        }
                    }
                }
                // 记录群成员的消息
                saveMessage(groupId, messageContent)
            }
        }

        // 启动定时任务
        GlobalScope.launch {
            while (true) {
                logger.info { "random message loading" }
                delay(Random.nextLong(weiRenConfig.random_from * 3600_000, weiRenConfig.random_to * 3600_000)) // 随机延迟
                myBot = Bot.instances.first()
                logger.info { "weiren: ${myBot.id}" }
                sendRandomMessage()
            }
        }

        GlobalScope.launch {
            while (true) {
                delay(TimeUnit.DAYS.toMillis(7)) // 每7天执行一次
                clearMessagesWeekly()
                logger.info { "clearMessagesWeekly" }
            }
        }

        GlobalScope.launch {
            while (true) {
                delay(TimeUnit.DAYS.toMillis(30)) // 每30天执行一次
                clearMessagesMonthly()
                logger.info { "clearMessagesMonthly" }
            }
        }

    }

    override fun onDisable() {
        weiRenCommand.unregister()
    }

    private val mutex = Mutex()

    private suspend fun saveMessage(groupId: Long, messageContent: String) {
        mutex.withLock {
            // 判断消息内容是否匹配黑名单
            for (pattern in weiRenConfig.blackList) {
                if (Regex(pattern).matches(messageContent)) {
                    return
                }
            }

            val groupHistories = weiRenData.histories.getOrPut(groupId) { mutableMapOf() }

            // 更新消息的权重
            val currentWeight = groupHistories.getOrDefault(messageContent, 0)
            if (currentWeight <= weiRenConfig.weight_limit) {
                groupHistories[messageContent] = currentWeight + 1
            }

            // 如果消息记录超过限制，移除权重最低的消息
            if (groupHistories.size > weiRenConfig.limit) {
                val minWeightMessage = groupHistories.minByOrNull { it.value }
                if (minWeightMessage != null) {
                    groupHistories.remove(minWeightMessage.key)
                }
            }
            weiRenData.histories[groupId] = groupHistories
            // 自动保存 FeedRecordData 的内容
            weiRenData.save()
        }
    }

    private fun extractKeywords(messageContent: String): String {
        // 使用正则表达式分隔字符串，去除所有标点符号和空格
        return messageContent.split(Regex(weiRenConfig.extractRegex)).filter { it.isNotEmpty() }.joinToString("")
    }

    private fun generateReply(groupId: Long, messageContent: String): String? {
        val groupHistories = weiRenData.histories[groupId] ?: return null

        // 字符串匹配
        val keywords = extractKeywords(messageContent)

        val candidates = mutableListOf<Pair<String, Int>>()
        for ((message, count) in groupHistories) {
            val distance = levenshteinDistance(keywords, extractKeywords(message))
            if (distance <= weiRenConfig.threshold) {
                candidates.add(Pair(message, count))
            }
        }

        // 根据权重选择回复
        if (candidates.isNotEmpty()) {
            return candidates.randomWeighted().first
        }
        return null
    }

    private fun <T> List<Pair<T, Int>>.randomWeighted(): Pair<T, Int> {
        val totalWeight = sumOf { it.second }
        var random = Random.nextInt(totalWeight)
        for (item in this) {
            random = random.minus(item.second)
            if (random < 0) {
                return item
            }
        }
        return this.last()
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = Array(lhsLength + 1) { it }
        var newCost = Array(lhsLength + 1) { 0 }

        for (i in 1..rhsLength) {
            newCost[0] = i

            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1

                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1

                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }

            val swap = cost
            cost = newCost
            newCost = swap
        }

        return cost[lhsLength]
    }
    // 定时任务，对所有群
    private suspend fun sendRandomMessage() {
        for ((groupId, groupHistories) in weiRenData.histories) {
            val randomMessage = groupHistories.keys.randomOrNull()

            if (randomMessage != null) {
                myBot.getGroup(groupId)?.sendMessage(PlainText(randomMessage))
                logger.info { "$groupId : message randomly" }
            }
        }
    }

    private suspend fun clearMessagesWeekly() {
        for ((groupId, groupHistories) in weiRenData.histories) {
            val messagesToRemove = groupHistories.filter { it.value <= weiRenConfig.importance_weekly }.keys
            messagesToRemove.forEach { groupHistories.remove(it) }
            weiRenData.histories[groupId] = groupHistories
        }
    }

    private suspend fun clearMessagesMonthly() {
        for ((groupId, groupHistories) in weiRenData.histories) {
            val messagesToRemove = groupHistories.filter { it.value <= weiRenConfig.importance_monthly }.keys
            messagesToRemove.forEach { groupHistories.remove(it) }
            weiRenData.histories[groupId] = groupHistories
        }
    }
    // 未匹配到消息时
    private fun selectRandomMessage(groupId: Long): String? {
        // 从 histories 中获取对应群组的消息历史记录
        val groupHistories = weiRenData.histories[groupId] ?: return null

        // 如果该群组有消息记录，随机选择一条
        return if (groupHistories.isNotEmpty()) {
            // 将消息和权重转换为列表，以便加权随机选择
            val weightedMessages = groupHistories.toList() // List<Pair<String, Int>>
            val randomMessagePair = weightedMessages.randomWeighted() // 使用加权随机选择的函数
            randomMessagePair.first
        } else {
            null
        }
    }
}
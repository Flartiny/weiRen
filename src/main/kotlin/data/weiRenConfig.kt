package org.example.mirai.plugin.data

import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object weiRenConfig:ReadOnlyPluginConfig(saveName = "weiRenConfig") {
    @ValueDescription("允许存储消息最大长度")
    val maxMessageLength: Int by value(128)

    @ValueDescription("用于相似度匹配")
    val threshold: Int by value(12)

    @ValueDescription("消息存储上限")
    val limit: Int by value(3000)

    @ValueDescription("回复概率")
    val probability: Double by value(0.05)

    @ValueDescription("每周清理权重小于importance_weekly的消息")
    val importance_weekly: Int by value(1)

    @ValueDescription("每月清理权重小于importance_monthly的消息")
    val importance_monthly: Int by value(5)

    @ValueDescription("词库黑名单(正则，匹配则不入选词库)")
    val blackList: MutableList<String> by value(defaultBlackList)

    @ValueDescription("分割关键词正则")
    val extractRegex: String by value("[\\s,。#.=/+!;:()\\[\\]{}\"]+")

    @ValueDescription("词条权重上限")
    val weight_limit: Int by value(15)

    @ValueDescription("随机消息时间区间下限(每_小时)")
    val random_from: Long by value(10L)

    @ValueDescription("随机消息时间区间上限(每_小时)")
    val random_to: Long by value(16L)

}

internal val defaultBlackList = mutableListOf(
    "运势",
    ".*/.*",
    "\\.system",
    ".*#.*",
    "^点歌",
    "今日塔罗",
    "单向历",
    "^转卡片",
    "开始添加"
)
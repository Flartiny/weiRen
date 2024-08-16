package org.example.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object weiRenData: AutoSavePluginData(saveName = "weiRenData"){
    @ValueDescription("群消息历史记录")
    val histories by value<MutableMap<Long,MutableMap<String,Int>>>()
}

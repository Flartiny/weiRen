package org.example.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import org.example.mirai.plugin.PluginMain
import org.example.mirai.plugin.data.weiRenConfig

@PublishedApi
internal object weiRenCommand: CompositeCommand (
    owner = PluginMain,
    primaryName = "weiren",
    description = "伪人插件配置"
){
    @SubCommand
    @Description("向黑名单中添加新的正则")
    suspend fun CommandSenderOnMessage<*>.add(regex: String){
        weiRenConfig.blackList.add(regex)
    }
}
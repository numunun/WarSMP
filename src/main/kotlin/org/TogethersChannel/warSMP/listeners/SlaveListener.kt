package org.TogethersChannel.warSMP.listeners

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent

class SlaveListener(private val plugin: WarSMP) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (plugin.slaves.containsKey(player.uniqueId)) {
            val masterTeam = plugin.slaves[player.uniqueId]
            player.sendMessage(Component.text("당신은 [$masterTeam] 팀의 노예입니다.", NamedTextColor.DARK_RED))
            // 접속 시 체력 및 효과 적용
            plugin.slaveEffectTask.applySlaveEffects(player)
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        if (plugin.slaves.containsKey(player.uniqueId)) {
            player.sendMessage(Component.text("노예로 리스폰합니다. 최대 체력이 5칸으로 제한됩니다.", NamedTextColor.DARK_RED))
            // 리스폰 시 체력 적용 (효과는 Task가 처리)
            plugin.slaveEffectTask.applySlaveEffects(player)
        }
    }
}
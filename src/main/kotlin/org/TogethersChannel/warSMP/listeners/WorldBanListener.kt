package org.TogethersChannel.warSMP.listeners

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerPortalEvent

class WorldBanListener(private val plugin: WarSMP) : Listener {

    @EventHandler
    fun onPortal(event: PlayerPortalEvent) {
        if (!plugin.isGameRunning) return

        // 엔드 월드로 이동하려는 경우
        if (event.to?.world?.environment == World.Environment.THE_END) {
            event.isCancelled = true
            event.player.sendMessage(Component.text("엔드 월드는 금지되었습니다.", NamedTextColor.RED))
        }
    }
}
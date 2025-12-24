package org.TogethersChannel.warSMP.listeners

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class PlayerDeathListener(private val plugin: WarSMP) : Listener {

    private val api = plugin.api

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        // 사망 메시지 색상 제거
        val originalMsg = event.deathMessage()
        if (originalMsg is TranslatableComponent) {
            val newArgs = originalMsg.args().map { arg ->
                if (arg is Component) {
                    arg.color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)
                } else {
                    arg
                }
            }
            event.deathMessage(originalMsg.args(newArgs))
        }

        if (!plugin.isGameRunning) {
            event.keepInventory = true
            event.drops.clear()
            return
        }

        val player = event.player

        if (plugin.slaves.containsKey(player.uniqueId)) {
            event.keepInventory = true
            event.keepLevel = true
            event.drops.clear()
            player.sendMessage(Component.text("노예는 죽어도 아이템을 잃지 않습니다.", NamedTextColor.GRAY))
            return
        }

        if (!plugin.isWarStarted) {
            event.keepInventory = true
            event.keepLevel = true
            event.drops.clear()
            player.sendMessage(Component.text("전쟁 시작 전이므로 인벤토리를 유지합니다.", NamedTextColor.GREEN))
            return
        }

        // [수정] 킬러 판정 로직 강화 (전투 로그 활용)
        var killer = player.killer
        if (killer == null) {
            // 최근 10초(10000ms) 내에 때린 사람이 있는지 확인
            val lastLog = plugin.lastAttacker[player.uniqueId]
            if (lastLog != null && System.currentTimeMillis() - lastLog.second < 10000) {
                killer = plugin.server.getPlayer(lastLog.first)
            }
        }

        // 1. PVE (진짜 자연사)
        if (killer == null) {
            event.keepInventory = true
            event.keepLevel = true
            event.drops.clear()
            player.sendMessage(Component.text("PVE 사망! 인벤토리를 [유지]합니다.", NamedTextColor.GREEN))
            return
        }

        // 2. PVP (팀원 또는 다른 팀)
        if (killer is Player) {
            val victimTeam = api.getPlayerTeamName(player)
            val killerTeam = api.getPlayerTeamName(killer)

            // 2a. 아군 팀킬
            if (victimTeam != null && victimTeam == killerTeam) {
                event.keepInventory = true
                event.keepLevel = true
                event.drops.clear()
                player.sendMessage(Component.text("같은 팀원에게 사망! 인벤토리를 [유지]합니다.", NamedTextColor.GREEN))
                return
            }

            // 2b. 적군에게 사망 (또는 전투 중 자연사)
            player.sendMessage(Component.text("PVP 사망!", NamedTextColor.RED))

            if (plugin.pvpKeepInventory) {
                event.keepInventory = true
                event.drops.clear()
                player.sendMessage(Component.text("서버 설정에 따라 인벤토리를 [유지]합니다.", NamedTextColor.GREEN))
            } else {
                event.keepInventory = false
                event.keepLevel = false
                event.droppedExp = (player.level * 7).coerceAtMost(100)
                player.sendMessage(Component.text("아이템과 레벨을 모두 [잃습니다].", NamedTextColor.RED))
            }

            // 노예 시스템 적용 및 멸망 확인
            if (victimTeam != null && killerTeam != null) {
                if (api.getTeamBeaconLocation(victimTeam) == null) {
                    plugin.slaves[player.uniqueId] = killerTeam

                    plugin.server.broadcast(
                        Component.text(player.name, NamedTextColor.YELLOW)
                            .append(Component.text(" 님이 ", NamedTextColor.WHITE))
                            .append(Component.text("[$killerTeam]", NamedTextColor.RED))
                            .append(Component.text(" 팀의 노예가 되었습니다!", NamedTextColor.WHITE))
                    )

                    plugin.slaveEffectTask.applySlaveEffects(player)

                    val remainingMembers = api.getLivingTeamMembers(victimTeam).filter {
                        !plugin.slaves.containsKey(it.uniqueId) && it.uniqueId != player.uniqueId
                    }

                    if (remainingMembers.isEmpty()) {
                        plugin.server.broadcast(
                            Component.text("=========================================", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                        )
                        plugin.server.broadcast(
                            Component.text("       [$victimTeam] 팀이 멸망했습니다!", NamedTextColor.RED, TextDecoration.BOLD)
                        )
                        plugin.server.broadcast(
                            Component.text("=========================================", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                        )
                        plugin.gameManager.checkWinCondition()
                    }
                }
            }
        }
    }
}
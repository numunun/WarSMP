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
        // 1. 사망 메시지 커스텀
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

        // 2. 게임 미실행 중 처리
        if (!plugin.isGameRunning) {
            event.keepInventory = true
            event.drops.clear()
            return
        }

        val player = event.player

        // 3. 전쟁 시작 전 처리
        if (!plugin.isWarStarted) {
            event.keepInventory = true
            event.keepLevel = true
            event.drops.clear()
            player.sendMessage(Component.text("전쟁 시작 전이므로 인벤토리를 유지합니다.", NamedTextColor.GREEN))
            return
        }

        // 4. 킬러 판정 로직
        var killer = player.killer
        if (killer == null) {
            val lastLog = plugin.lastAttacker[player.uniqueId]
            if (lastLog != null && System.currentTimeMillis() - lastLog.second < 10000) {
                killer = plugin.server.getPlayer(lastLog.first)
            }
        }

        // 5. PVE 사망 처리
        if (killer == null) {
            event.keepInventory = true
            event.keepLevel = true
            event.drops.clear()
            player.sendMessage(Component.text("PVE 사망! 인벤토리를 [유지]합니다.", NamedTextColor.GREEN))
            return
        }

        // 6. PVP 사망 처리
        if (killer is Player) {
            val victimTeam = api.getPlayerTeamName(player)
            val killerTeam = api.getPlayerTeamName(killer)

            if (victimTeam != null && victimTeam == killerTeam) {
                event.keepInventory = true
                event.keepLevel = true
                event.drops.clear()
                player.sendMessage(Component.text("같은 팀원에게 사망! 인벤토리를 [유지]합니다.", NamedTextColor.GREEN))
                return
            }

            player.sendMessage(Component.text("적군에게 사망했습니다!", NamedTextColor.RED))

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

            // 7. 팀 생존/멸망 확인 로직 [핵심 수정]
            if (victimTeam != null) {
                val hasBeacon = api.getTeamBeaconLocation(victimTeam) != null

                // [수정 포인트] plugin.beaconListener 뒤에 ?. 을 붙이고 == true 체크를 추가함
                val isRebuilding = plugin.beaconListener?.isTeamRebuilding(victimTeam) == true

                val remainingMembers = api.getLivingTeamMembers(victimTeam).filter {
                    it.uniqueId != player.uniqueId
                }

                if (!hasBeacon && !isRebuilding && remainingMembers.isEmpty()) {
                    plugin.server.broadcast(
                        Component.text("=========================================", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    )
                    plugin.server.broadcast(
                        Component.text("       [$victimTeam] 팀이 최종적으로 멸망했습니다!", NamedTextColor.RED, TextDecoration.BOLD)
                    )
                    plugin.server.broadcast(
                        Component.text("=========================================", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    )
                    plugin.gameManager.checkWinCondition()
                } else if (!hasBeacon && isRebuilding) {
                    player.sendMessage(Component.text("현재 팀 신호기가 없지만, 재건 시간이 남아있어 탈락하지 않았습니다.", NamedTextColor.YELLOW))
                }
            }
        }
    }
}
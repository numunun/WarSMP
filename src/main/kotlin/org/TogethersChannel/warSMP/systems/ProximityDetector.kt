package org.TogethersChannel.warSMP.systems

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

class ProximityDetector(private val plugin: WarSMP) {

    private var task: BukkitTask? = null
    private val api = plugin.api

    fun start() {
        task = object : BukkitRunnable() {
            override fun run() {
                if (!plugin.isGameRunning) {
                    this.cancel()
                    return
                }

                plugin.server.onlinePlayers.forEach { player ->
                    // [수정] warstart 유무와 상관없이 두 기능 모두 항시 작동
                    checkNearbyEnemies(player) // 1. 플레이어 감지 (20칸)
                    checkNearbyBeacons(player) // 2. 신호기 감지 (100칸)
                }
            }
            // 1초(20틱)마다 검사. (서버 부하가 심하면 40L (2초)로 늘릴 수 있습니다)
        }.runTaskTimer(plugin, 0L, 20L)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    /**
     * 기능 1: 20칸 내의 적 플레이어 감지 (워든 심장)
     */
    private fun checkNearbyEnemies(player: Player) {
        val nearbyEnemies = player.world.getNearbyEntities(player.location, 20.0, 20.0, 20.0)
            .filterIsInstance<Player>()
            .filter { it != player && !api.arePlayersInSameTeam(player, it) }

        if (nearbyEnemies.isNotEmpty()) {
            player.playSound(player.location, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 1.0f)
            player.sendActionBar(
                Component.text("[ ♥ ]", NamedTextColor.RED, TextDecoration.BOLD)
            )
        }
    }

    /**
     * 기능 2: 100칸 내의 적 신호기 감지 (발광)
     */
    private fun checkNearbyBeacons(player: Player) {
        val myTeamName = api.getPlayerTeamName(player)
        val allTeamNames = api.getAllTeamNames()

        for (teamName in allTeamNames) {
            if (teamName == myTeamName) continue // 우리 팀 신호기는 무시

            val beaconLoc = api.getTeamBeaconLocation(teamName) ?: continue // 신호기가 없으면 무시

            // 같은 월드이고 100블럭 이내인지 확인
            if (beaconLoc.world == player.world && beaconLoc.distance(player.location) <= 100.0) {
                // 1. 접근자(player)에게 발광 적용
                // (2초 지속, 파티클/아이콘 숨김)
                player.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false))
                player.sendActionBar(Component.text("[$teamName] 팀의 신호기에 접근했습니다!", NamedTextColor.YELLOW))

                // 2. 해당 팀원들에게 알림
                api.getLivingTeamMembers(teamName).forEach { member ->
                    member.sendActionBar(
                        Component.text(player.name, NamedTextColor.RED)
                            .append(Component.text(" 님이 신호기 근처에 접근했습니다!", NamedTextColor.YELLOW))
                    )
                }
            }
        }
    }
}
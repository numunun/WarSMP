package org.TogethersChannel.warSMP.game

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.*

class GameManager(private val plugin: WarSMP) {
    private var peaceTask: BukkitRunnable? = null
    private val api = plugin.api

    fun startGame() {
        if (plugin.isGameRunning) return
        plugin.isGameRunning = true
        plugin.server.broadcast(Component.text("전쟁 게임이 시작되었습니다!", NamedTextColor.GOLD))
        plugin.server.worlds.forEach { it.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false) }

        scatterPlayers()
        startPeaceTime()
        giveInitialBeacons()

        // [수정] Safe Call(?.)을 사용하여 접근 제한 에러 해결
        plugin.proximityDetector?.start()
    }

    fun stopGame(forced: Boolean) {
        if (!plugin.isGameRunning) return
        plugin.isGameRunning = false

        peaceTask?.cancel()
        plugin.isPeaceTime = false
        plugin.isWarStarted = false

        // [수정] 노예 관련 리스트 제거(slaves.clear)는 이제 필요 없음 (이미 변수 삭제됨)

        // [수정] Safe Call(?.) 사용
        plugin.proximityDetector?.stop()

        // [수정] BeaconListener 타이머 취소 (Safe Call)
        plugin.beaconListener?.cancelAllRebuildTimers()

        plugin.server.broadcast(Component.text(if (forced) "게임이 강제 종료되었습니다." else "게임이 종료되었습니다.", NamedTextColor.RED))
        plugin.server.worlds.forEach { it.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true) }
    }

    private fun scatterPlayers() {
        val world = plugin.server.worlds.firstOrNull() ?: return
        val border = world.worldBorder
        val radius = if (plugin.scatterRadius > 0) plugin.scatterRadius.toDouble() else (border.size / 2.0) * 0.9
        val players = plugin.server.onlinePlayers.toList()
        if (players.isEmpty()) return

        val locations = generateScatterLocations(players.size, border.center, radius)
        players.forEachIndexed { i, p ->
            val safe = world.getHighestBlockAt(locations[i]).location.add(0.5, 1.0, 0.5)
            p.teleport(safe)
            p.setBedSpawnLocation(safe, true)
            p.gameMode = GameMode.SURVIVAL
            p.inventory.clear()
            p.health = 20.0
            p.foodLevel = 20
        }
    }

    private fun generateScatterLocations(count: Int, center: Location, radius: Double): List<Location> {
        val list = mutableListOf<Location>()
        val phi = (1 + sqrt(5.0)) / 2
        for (i in 0 until count) {
            val r = sqrt(i.toDouble() / count) * radius
            val theta = 2 * Math.PI * i / phi
            list.add(Location(center.world, center.x + r * cos(theta), 0.0, center.z + r * sin(theta)))
        }
        return list
    }

    private fun giveInitialBeacons() {
        plugin.server.onlinePlayers.forEach {
            if (api.isTeamLeader(it)) {
                it.inventory.addItem(ItemStack(Material.BEACON))
                it.sendMessage(Component.text("팀 신호기가 지급되었습니다. 안전한 위치에 설치하세요!", NamedTextColor.GREEN))
            }
        }
    }

    private fun startPeaceTime() {
        plugin.isPeaceTime = true
        var time = plugin.peaceTimeSeconds
        peaceTask = object : BukkitRunnable() {
            override fun run() {
                if (time <= 0) {
                    plugin.isPeaceTime = false
                    plugin.isWarStarted = true
                    plugin.server.broadcast(Component.text("평화 시간이 종료되었습니다! 이제 PVP와 신호기 파괴가 가능합니다.", NamedTextColor.RED))
                    cancel()
                    return
                }
                if (time % 60 == 0 || (time <= 10 && time > 0)) {
                    plugin.server.showTitle(Title.title(
                        Component.text("평화 시간 종료까지", NamedTextColor.YELLOW),
                        Component.text("$time 초", NamedTextColor.RED)
                    ))
                }
                time--
            }
        }.apply { runTaskTimer(plugin, 0, 20) }
    }

    /**
     * 승리 조건 체크
     * 노예 로직을 완전히 제거하고, 오직 신호기 보유 여부와 재건 여부로만 판정합니다.
     */
    fun checkWinCondition() {
        if (!plugin.isGameRunning || plugin.isPeaceTime) return

        val activeTeams = api.getAllTeamNames().filter { teamName ->
            val hasBeacon = api.getTeamBeaconLocation(teamName) != null
            val isRebuilding = plugin.beaconListener?.isTeamRebuilding(teamName) == true

            hasBeacon || isRebuilding
        }

        if (activeTeams.size == 1) {
            val winner = activeTeams[0]

            // [수정] 스크린샷에 있던 slaves 관련 체크 코드를 삭제함
            plugin.server.broadcast(
                Component.text("\n🏆 [ ", NamedTextColor.GOLD)
                    .append(Component.text(winner, NamedTextColor.AQUA))
                    .append(Component.text(" ] 팀이 최후의 승자가 되었습니다! 🏆\n", NamedTextColor.GOLD))
            )
            stopGame(false)
        }
        else if (activeTeams.isEmpty()) {
            plugin.server.broadcast(Component.text("모든 팀의 신호기가 파괴되어 승자 없이 게임이 종료되었습니다.", NamedTextColor.GRAY))
            stopGame(false)
        }
    }
}
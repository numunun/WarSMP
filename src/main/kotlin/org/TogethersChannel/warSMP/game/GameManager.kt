package org.TogethersChannel.warSMP.game

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration
import kotlin.math.*

class GameManager(private val plugin: WarSMP) {
    private var peaceTask: BukkitRunnable? = null
    private val api = plugin.api

    fun startGame() {
        if (plugin.isGameRunning) return
        plugin.isGameRunning = true
        plugin.server.broadcast(Component.text("게임 시작", NamedTextColor.GOLD))
        plugin.server.worlds.forEach { it.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false) }
        scatterPlayers()
        startPeaceTime()
        giveInitialBeacons()
        plugin.proximityDetector.start()
    }

    fun stopGame(forced: Boolean) {
        if (!plugin.isGameRunning) return
        plugin.isGameRunning = false
        peaceTask?.cancel()
        plugin.isPeaceTime = false
        plugin.isWarStarted = false
        plugin.slaves.clear()
        plugin.proximityDetector.stop()

        // BeaconListener에 구현한 함수 호출
        plugin.beaconListener.cancelAllRebuildTimers()

        plugin.slaveEffectTask.clearSlaves()
        plugin.server.broadcast(Component.text(if (forced) "강제 종료" else "게임 종료", NamedTextColor.RED))
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
        plugin.server.onlinePlayers.forEach { if (api.isTeamLeader(it)) it.inventory.addItem(ItemStack(Material.BEACON)) }
    }

    private fun startPeaceTime() {
        plugin.isPeaceTime = true
        var time = plugin.peaceTimeSeconds
        peaceTask = object : BukkitRunnable() {
            override fun run() {
                if (time <= 0) {
                    plugin.isPeaceTime = false
                    plugin.server.broadcast(Component.text("PVP 활성화", NamedTextColor.RED))
                    cancel()
                    return
                }
                if (time % 60 == 0 || time <= 10) {
                    plugin.server.showTitle(Title.title(Component.text("평화 시간"), Component.text("$time 초")))
                }
                time--
            }
        }.apply { runTaskTimer(plugin, 0, 20) }
    }

    fun checkWinCondition() {
        if (!plugin.isWarStarted) return
        val activeTeams = api.getAllTeamNames().filter {
            // BeaconListener에 구현한 함수 호출
            api.getTeamBeaconLocation(it) != null || plugin.beaconListener.isTeamRebuilding(it)
        }

        if (activeTeams.size == 1) {
            val winner = activeTeams[0]
            val othersAlive = plugin.server.onlinePlayers.any {
                api.getPlayerTeamName(it) != winner && !plugin.slaves.containsKey(it.uniqueId)
            }
            if (!othersAlive) {
                plugin.server.broadcast(Component.text("승리: $winner", NamedTextColor.GOLD))
                stopGame(false)
            }
        } else if (activeTeams.isEmpty()) {
            plugin.server.broadcast(Component.text("무승부", NamedTextColor.GRAY))
            stopGame(false)
        }
    }
}
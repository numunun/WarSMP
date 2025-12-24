package org.TogethersChannel.warSMP.listeners

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.EulerAngle
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class BeaconListener(private val plugin: WarSMP) : Listener {

    private val api = plugin.api

    // BukkitTask를 저장하는 맵
    private val rebuildTimers = mutableMapOf<String, BukkitTask>()
    val tpCooldown = mutableMapOf<UUID, String>()

    // 사망 처리 관련 변수
    private val deathLocations = mutableMapOf<UUID, Location>()
    private val respawnTimers = mutableSetOf<UUID>()
    private val mannequins = mutableMapOf<UUID, ArmorStand>()

    // === [추가된 함수들] ===

    /**
     * 특정 팀이 현재 비콘 재건 타이머가 돌아가고 있는지 확인합니다.
     */
    fun isTeamRebuilding(teamName: String): Boolean {
        return rebuildTimers.containsKey(teamName)
    }

    /**
     * 게임 종료 시 모든 재건 타이머를 강제로 중지하고 맵을 비웁니다.
     */
    fun cancelAllRebuildTimers() {
        rebuildTimers.values.forEach { it.cancel() }
        rebuildTimers.clear()
    }

    // ========================

    @EventHandler
    fun onDeath(e: PlayerDeathEvent) {
        if (!plugin.isGameRunning) return
        deathLocations[e.player.uniqueId] = e.player.location
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        if (mannequins.containsKey(e.player.uniqueId)) {
            mannequins[e.player.uniqueId]?.remove()
            mannequins.remove(e.player.uniqueId)
        }
    }

    @EventHandler
    fun onPlace(e: BlockPlaceEvent) {
        if (!plugin.isGameRunning) return
        if (e.block.type != Material.BEACON) return

        val player = e.player

        if (plugin.isWarStarted) {
            player.sendMessage(Component.text("전쟁이 시작된 후에는 신호기를 설치(이동)할 수 없습니다.", NamedTextColor.RED))
            e.isCancelled = true
            return
        }

        if (e.block.y < -64 || e.block.y > 100) {
            player.sendMessage(Component.text("신호기는 Y좌표 -64에서 100 사이에만 설치할 수 있습니다.", NamedTextColor.RED))
            e.isCancelled = true
            return
        }

        if (!api.isTeamLeader(player)) {
            player.sendMessage(Component.text("팀장만 신호기를 설치할 수 있습니다.", NamedTextColor.RED))
            e.isCancelled = true
            return
        }

        val teamName = api.getPlayerTeamName(player) ?: return

        val beaconLoc = e.block.location
        api.setTeamBeaconLocation(teamName, beaconLoc)

        val world = beaconLoc.world
        val bx = beaconLoc.blockX
        val by = beaconLoc.blockY
        val bz = beaconLoc.blockZ

        for (x in -1..1) {
            for (z in -1..1) {
                world.getBlockAt(bx + x, by - 1, bz + z).type = Material.IRON_BLOCK
            }
        }

        api.getLivingTeamMembers(teamName).forEach { member ->
            member.setBedSpawnLocation(beaconLoc, true)
            member.sendMessage(Component.text("[$teamName] 팀의 신호기가 설치되었습니다! (기본 활성화)", NamedTextColor.GREEN))
        }

        if (rebuildTimers.containsKey(teamName)) {
            rebuildTimers.remove(teamName)?.cancel()
            notifyTeamRebuild(teamName)
        }
    }

    @EventHandler
    fun onBreak(e: BlockBreakEvent) {
        if (!plugin.isGameRunning) return
        if (e.block.type != Material.BEACON) return

        val breaker = e.player
        val location = e.block.location

        if (!plugin.isWarStarted) {
            breaker.sendMessage(Component.text("전쟁이 시작되어야 신호기를 파괴할 수 있습니다.", NamedTextColor.RED))
            e.isCancelled = true
            return
        }

        val targetTeamName = api.getTeamByBeaconLocation(location) ?: return

        if (api.getPlayerTeamName(breaker) == targetTeamName) {
            breaker.sendMessage(Component.text("자신의 팀 신호기는 파괴할 수 없습니다!", NamedTextColor.RED))
            e.isCancelled = true
            return
        }

        api.setTeamBeaconLocation(targetTeamName, null)
        plugin.server.broadcast(Component.text("[$targetTeamName] 팀의 신호기가 파괴되었습니다!", NamedTextColor.RED, TextDecoration.BOLD))

        startRebuildTimer(targetTeamName)
        plugin.gameManager.checkWinCondition()
    }

    @EventHandler
    fun onRespawn(e: PlayerRespawnEvent) {
        if (!plugin.isGameRunning) return
        val player = e.player

        if (plugin.slaves.containsKey(player.uniqueId)) return

        val teamName = api.getPlayerTeamName(player) ?: return
        val beaconLoc = api.getTeamBeaconLocation(teamName)

        var finalDestination: Location? = null

        if (beaconLoc != null) {
            finalDestination = beaconLoc.clone().add(0.5, 1.0, 0.5)
        } else {
            if (!plugin.isWarStarted) {
                player.sendMessage(Component.text("신호기가 파괴되어 리스폰할 수 없습니다. 당신은 탈락했습니다.", NamedTextColor.RED))
                player.gameMode = GameMode.SPECTATOR
                return
            } else {
                player.sendMessage(Component.text("팀의 신호기가 파괴되었습니다. 리스폰 지점을 잃었습니다!", NamedTextColor.RED))

                val world = player.world
                val border = world.worldBorder
                val size = border.size / 2.0

                val x = border.center.x + ThreadLocalRandom.current().nextDouble(-size, size)
                val z = border.center.z + ThreadLocalRandom.current().nextDouble(-size, size)
                val y = world.getHighestBlockYAt(x.toInt(), z.toInt()) + 1.0
                finalDestination = Location(world, x, y, z)
            }
        }

        if (finalDestination != null) {
            val deathLoc = deathLocations[player.uniqueId] ?: finalDestination

            if (plugin.isWarStarted) {
                e.respawnLocation = deathLoc
                object : BukkitRunnable() {
                    override fun run() {
                        startFrozenRespawn(player, deathLoc, finalDestination!!)
                    }
                }.runTaskLater(plugin, 2L)
            } else {
                e.respawnLocation = finalDestination
            }
        }
    }

    private fun startFrozenRespawn(player: org.bukkit.entity.Player, stayLoc: Location, targetLoc: Location) {
        if (respawnTimers.contains(player.uniqueId)) return
        respawnTimers.add(player.uniqueId)

        player.gameMode = GameMode.ADVENTURE
        player.allowFlight = true
        player.isFlying = true

        plugin.server.onlinePlayers.forEach {
            if (it.uniqueId != player.uniqueId) it.hidePlayer(plugin, player)
        }

        val mannequin = stayLoc.world.spawnEntity(stayLoc.clone().subtract(0.0, 1.5, 0.0), EntityType.ARMOR_STAND) as ArmorStand
        mannequin.isVisible = false
        mannequin.isSmall = true
        mannequin.setGravity(false)
        mannequin.isInvulnerable = true

        val skull = ItemStack(Material.PLAYER_HEAD)
        val meta = skull.itemMeta as SkullMeta
        meta.owningPlayer = player
        skull.itemMeta = meta
        mannequin.equipment.helmet = skull
        mannequin.headPose = EulerAngle(Math.toRadians(90.0), 0.0, 0.0)

        mannequins[player.uniqueId] = mannequin

        object : BukkitRunnable() {
            var ticksRun = 0
            val maxTicks = 60 * 20
            val frozenLoc = stayLoc.clone()

            override fun run() {
                if (!player.isOnline) {
                    mannequin.remove()
                    respawnTimers.remove(player.uniqueId)
                    mannequins.remove(player.uniqueId)
                    this.cancel()
                    return
                }

                player.teleport(frozenLoc)

                if (ticksRun % 20 == 0) {
                    val secondsLeft = (maxTicks - ticksRun) / 20
                    val titleText = Component.text("부활 대기 중...", NamedTextColor.RED)
                    val subtitleText = Component.text("${secondsLeft}초 남았습니다.", NamedTextColor.YELLOW)
                    player.showTitle(Title.title(titleText, subtitleText, Title.Times.times(Duration.ZERO, Duration.ofMillis(100), Duration.ZERO)))
                }

                ticksRun++

                if (ticksRun >= maxTicks) {
                    mannequin.remove()
                    mannequins.remove(player.uniqueId)

                    player.teleport(targetLoc)
                    player.gameMode = GameMode.SURVIVAL
                    player.allowFlight = false
                    player.isFlying = false

                    plugin.server.onlinePlayers.forEach { it.showPlayer(plugin, player) }

                    player.showTitle(Title.title(
                        Component.text("부활!", NamedTextColor.GREEN),
                        Component.text("전장으로 복귀합니다.", NamedTextColor.GRAY)
                    ))
                    player.sendMessage(Component.text("부활했습니다!", NamedTextColor.GREEN))

                    respawnTimers.remove(player.uniqueId)
                    this.cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    private fun startRebuildTimer(teamName: String) {
        if (rebuildTimers.containsKey(teamName)) return

        rebuildTimers[teamName] = object : BukkitRunnable() {
            var timeLeft = 3600

            override fun run() {
                val leaderUUID = api.getTeamLeader(teamName) ?: run { this.cancel(); return }
                val leader = plugin.server.getPlayer(leaderUUID)

                if (leader == null || !leader.isOnline || leader.isDead) {
                    plugin.server.broadcast(Component.text("[$teamName] 팀이 재건 조건(팀장 생존)을 만족하지 못해 재건에 실패했습니다.", NamedTextColor.GRAY))
                    rebuildTimers.remove(teamName)
                    this.cancel()
                    plugin.gameManager.checkWinCondition()
                    return
                }

                val min = timeLeft / 60
                val sec = timeLeft % 60
                val timeString = String.format("%02d분 %02d초", min, sec)

                api.getLivingTeamMembers(teamName).forEach {
                    it.sendActionBar(Component.text("재건 생존 시간: $timeString", NamedTextColor.GOLD))
                }

                if (timeLeft == 1800 || timeLeft == 600 || timeLeft == 300 || timeLeft == 60 || timeLeft == 30 || timeLeft == 10) {
                    plugin.server.broadcast(Component.text("[$teamName] 팀 재건 성공까지 ", NamedTextColor.YELLOW)
                        .append(Component.text(if(timeLeft >= 60) "${timeLeft/60}분" else "${timeLeft}초", NamedTextColor.RED))
                        .append(Component.text(" 남았습니다.", NamedTextColor.YELLOW)))
                }

                timeLeft--

                if (timeLeft <= 0) {
                    leader.inventory.addItem(ItemStack(Material.BEACON, 1))
                    leader.sendMessage(Component.text("1시간 생존에 성공하여 신호기를 지급받았습니다!", NamedTextColor.GOLD))
                    rebuildTimers.remove(teamName)
                    this.cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    private fun notifyTeamRebuild(teamName: String) {
        val members = api.getLivingTeamMembers(teamName)
        members.forEach { member ->
            if (api.isTeamLeader(member)) {
                member.sendMessage(Component.text("[$teamName] 팀이 재건되었습니다!", NamedTextColor.GREEN))
            } else {
                member.sendMessage(Component.text("[$teamName] 팀이 재건되었습니다! 10분간 /tptochief 명령어로 팀장에게 이동할 수 있습니다.", NamedTextColor.GREEN))
                tpCooldown[member.uniqueId] = teamName
            }
        }

        object : BukkitRunnable() {
            override fun run() {
                members.forEach { tpCooldown.remove(it.uniqueId) }
                val message = Component.text("[$teamName] 팀의 /tptochief 사용 시간이 만료되었습니다.", NamedTextColor.YELLOW)
                plugin.server.onlinePlayers.forEach { player ->
                    if (api.getPlayerTeamName(player) == teamName) {
                        player.sendMessage(message)
                    }
                }
            }
        }.runTaskLater(plugin, 20L * 60 * 10)
    }
}
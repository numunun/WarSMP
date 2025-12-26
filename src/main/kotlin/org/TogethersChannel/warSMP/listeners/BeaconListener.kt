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
import org.bukkit.entity.Player
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
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class BeaconListener(private val plugin: WarSMP) : Listener {

    private val api = plugin.api

    // 타이머 관리 (내부에서만 사용)
    private val rebuildTimers = mutableMapOf<String, BukkitTask>()

    // [수정] TpToChiefCommand에서 접근할 수 있도록 private 제거 (public으로 변경)
    val tpCooldown = mutableMapOf<UUID, String>()

    // 부활 처리 관련 (마네킹 시스템)
    private val deathLocations = mutableMapOf<UUID, Location>()
    private val respawnTimers = mutableSetOf<UUID>()
    private val mannequins = mutableMapOf<UUID, ArmorStand>()

    /**
     * 특정 팀이 현재 재건 타이머(2시간)가 돌아가고 있는지 확인
     */
    fun isTeamRebuilding(teamName: String): Boolean = rebuildTimers.containsKey(teamName)

    /**
     * [수정] 게임 종료 시 모든 타이머와 권한 데이터를 초기화
     */
    fun cancelAllRebuildTimers() {
        rebuildTimers.values.forEach { it.cancel() }
        rebuildTimers.clear()
        tpCooldown.clear() // TP 권한도 함께 초기화

        // 마네킹 제거 로직 추가 (게임 강제 종료 시 월드에 남지 않게 함)
        mannequins.values.forEach { it.remove() }
        mannequins.clear()
        respawnTimers.clear()
    }

    /**
     * 특정 플레이어가 팀장에게 TP 가능한 상태인지 확인
     */
    fun canTeleportToChief(player: Player): Boolean = tpCooldown.containsKey(player.uniqueId)

    @EventHandler
    fun onDeath(e: PlayerDeathEvent) {
        if (!plugin.isGameRunning) return
        deathLocations[e.player.uniqueId] = e.player.location
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        mannequins[e.player.uniqueId]?.remove()
        mannequins.remove(e.player.uniqueId)
    }

    @EventHandler
    fun onPlace(e: BlockPlaceEvent) {
        if (!plugin.isGameRunning) return
        if (e.block.type != Material.BEACON) return

        val player = e.player
        val teamName = api.getPlayerTeamName(player) ?: return

        // 1. 전쟁 시작 전/후 체크
        if (plugin.isWarStarted && !isTeamRebuilding(teamName)) {
            // 이미 신호기가 설치되어 있는 상태에서 또 설치하려고 할 때 (이동 방지)
            if (api.getTeamBeaconLocation(teamName) != null) {
                player.sendMessage(Component.text("전쟁 중에는 신호기를 임의로 이동할 수 없습니다.", NamedTextColor.RED))
                e.isCancelled = true
                return
            }
        }

        // 2. 팀장 권한 체크
        if (!api.isTeamLeader(player)) {
            player.sendMessage(Component.text("신호기는 팀장만 설치할 수 있습니다.", NamedTextColor.RED))
            e.isCancelled = true
            return
        }

        // 3. 신호기 설치 및 피라미드 자동 생성
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

        // 4. 재건 성공 처리 (타이머 중지)
        if (isTeamRebuilding(teamName)) {
            rebuildTimers[teamName]?.cancel()
            rebuildTimers.remove(teamName)
            notifyTeamRebuild(teamName)
        } else {
            api.getLivingTeamMembers(teamName).forEach { member ->
                member.sendMessage(Component.text("[$teamName] 팀의 신호기가 설치되었습니다!", NamedTextColor.GREEN))
            }
        }
    }

    @EventHandler
    fun onBreak(e: BlockBreakEvent) {
        if (!plugin.isGameRunning) return
        if (e.block.type != Material.BEACON) return

        val breaker = e.player
        val location = e.block.location
        // [참고] API 메서드 명칭이 getTeamByBeaconLocation 인지 확인 필요 (제공해주신 코드 기준)
        val targetTeamName = api.getTeamByBeaconLocation(location) ?: return

        if (!plugin.isWarStarted) {
            breaker.sendMessage(Component.text("전쟁이 시작되어야 신호기를 파괴할 수 있습니다.", NamedTextColor.RED))
            e.isCancelled = true
            return
        }

        if (api.getPlayerTeamName(breaker) == targetTeamName) {
            breaker.sendMessage(Component.text("자신의 팀 신호기는 파괴할 수 없습니다!", NamedTextColor.RED))
            e.isCancelled = true
            return
        }

        // 신호기 파괴 실행
        api.setTeamBeaconLocation(targetTeamName, null)

        // 팀 전원 알림 및 타이머 시작
        startRebuildTimer(targetTeamName)
        plugin.gameManager.checkWinCondition()
    }

    @EventHandler
    fun onRespawn(e: PlayerRespawnEvent) {
        if (!plugin.isGameRunning) return
        val player = e.player

        val teamName = api.getPlayerTeamName(player) ?: return
        val beaconLoc = api.getTeamBeaconLocation(teamName)

        var finalDestination: Location? = null

        if (beaconLoc != null) {
            finalDestination = beaconLoc.clone().add(0.5, 1.0, 0.5)
        } else {
            // 신호기 없을 때: 랜덤 리스폰
            val world = player.world
            val border = world.worldBorder
            val size = border.size / 2.0
            val x = border.center.x + ThreadLocalRandom.current().nextDouble(-size, size)
            val z = border.center.z + ThreadLocalRandom.current().nextDouble(-size, size)
            val y = world.getHighestBlockYAt(x.toInt(), z.toInt()) + 1.0
            finalDestination = Location(world, x, y, z)
            player.sendMessage(Component.text("팀의 신호기가 없어 무작위 위치에서 부활합니다!", NamedTextColor.RED))
        }

        val deathLoc = deathLocations[player.uniqueId] ?: finalDestination!!
        if (plugin.isWarStarted) {
            e.respawnLocation = deathLoc
            object : BukkitRunnable() {
                override fun run() { startFrozenRespawn(player, deathLoc, finalDestination!!) }
            }.runTaskLater(plugin, 2L)
        } else {
            e.respawnLocation = finalDestination!!
        }
    }

    private fun startFrozenRespawn(player: Player, stayLoc: Location, targetLoc: Location) {
        if (respawnTimers.contains(player.uniqueId)) return
        respawnTimers.add(player.uniqueId)

        player.gameMode = GameMode.ADVENTURE
        player.allowFlight = true
        player.isFlying = true

        val mannequin = stayLoc.world.spawnEntity(stayLoc.clone().subtract(0.0, 1.5, 0.0), EntityType.ARMOR_STAND) as ArmorStand
        mannequin.isVisible = false
        mannequin.isSmall = true
        mannequin.setGravity(false)
        val skull = ItemStack(Material.PLAYER_HEAD).apply {
            itemMeta = (itemMeta as SkullMeta).apply { owningPlayer = player }
        }
        mannequin.equipment.helmet = skull
        mannequin.headPose = EulerAngle(Math.toRadians(90.0), 0.0, 0.0)
        mannequins[player.uniqueId] = mannequin

        object : BukkitRunnable() {
            var ticksRun = 0
            val maxTicks = 60 * 20 // 1분

            override fun run() {
                if (!player.isOnline) {
                    mannequin.remove()
                    respawnTimers.remove(player.uniqueId)
                    this.cancel()
                    return
                }
                player.teleport(stayLoc)
                if (ticksRun % 20 == 0) {
                    player.showTitle(Title.title(Component.text("부활 대기 중...", NamedTextColor.RED), Component.text("${(maxTicks - ticksRun) / 20}초 남았습니다.", NamedTextColor.YELLOW)))
                }
                if (++ticksRun >= maxTicks) {
                    mannequin.remove()
                    mannequins.remove(player.uniqueId)
                    player.teleport(targetLoc)
                    player.gameMode = GameMode.SURVIVAL
                    player.allowFlight = false
                    player.isFlying = false
                    respawnTimers.remove(player.uniqueId)
                    this.cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    private fun startRebuildTimer(teamName: String) {
        val message = Component.text()
            .append(Component.text("\n[ ! ] ", NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.text("$teamName 팀의 신호기가 파괴되었습니다!", NamedTextColor.WHITE))
            .append(Component.text("\n- 2시간의 팀 재건 시간이 시작됩니다.", NamedTextColor.YELLOW))
            .append(Component.text("\n- 초기 30분은 재건 불가 시간이며, 이후 팀장에게 신호기가 지급됩니다.\n", NamedTextColor.GRAY))
            .build()

        api.getLivingTeamMembers(teamName).forEach { it.sendMessage(message) }

        rebuildTimers[teamName]?.cancel()

        rebuildTimers[teamName] = object : BukkitRunnable() {
            var secondsElapsed = 0
            val lockSeconds = 1800 // 30분
            val totalSeconds = 7200 // 2시간

            override fun run() {
                secondsElapsed++

                if (secondsElapsed == lockSeconds) {
                    val leaderUUID = api.getTeamLeader(teamName) ?: return
                    val leader = plugin.server.getPlayer(leaderUUID)
                    leader?.let {
                        it.inventory.addItem(ItemStack(Material.BEACON))
                        it.sendMessage(Component.text("[!] 재건 불가 시간이 종료되어 신호기가 지급되었습니다!", NamedTextColor.GOLD, TextDecoration.BOLD))
                    }
                    plugin.server.broadcast(Component.text("[$teamName] 팀의 신호기 재건이 이제 가능합니다!", NamedTextColor.YELLOW))
                }

                val remaining = totalSeconds - secondsElapsed
                val min = remaining / 60
                val sec = remaining % 60
                val status = if (secondsElapsed < lockSeconds) "재건 불가 (잠금: ${(lockSeconds - secondsElapsed)/60}분)" else "재건 가능"
                val color = if (secondsElapsed < lockSeconds) NamedTextColor.RED else NamedTextColor.GREEN

                api.getLivingTeamMembers(teamName).forEach {
                    it.sendActionBar(Component.text("상태: $status | 남은 시간: ${min}분 ${sec}초", color))
                }

                if (secondsElapsed >= totalSeconds) {
                    plugin.server.broadcast(Component.text("[$teamName] 팀이 2시간 내에 신호기를 재건하지 못해 최종 탈락했습니다!", NamedTextColor.RED, TextDecoration.BOLD))
                    rebuildTimers.remove(teamName)
                    this.cancel()
                    plugin.gameManager.checkWinCondition()
                }
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    private fun notifyTeamRebuild(teamName: String) {
        api.getLivingTeamMembers(teamName).forEach { member ->
            if (api.isTeamLeader(member)) {
                member.sendMessage(Component.text("✔ 팀 신호기를 다시 설치하여 팀이 재건되었습니다!", NamedTextColor.GREEN))
            } else {
                member.sendMessage(Component.text("✔ 팀이 재건되었습니다! 10분 동안 /tptochief 명령어로 팀장에게 이동할 수 있습니다.", NamedTextColor.AQUA))
                tpCooldown[member.uniqueId] = teamName
            }
        }

        object : BukkitRunnable() {
            override fun run() {
                api.getLivingTeamMembers(teamName).forEach {
                    if (tpCooldown.containsKey(it.uniqueId)) {
                        tpCooldown.remove(it.uniqueId)
                        it.sendMessage(Component.text("팀장에게 이동할 수 있는 시간이 만료되었습니다.", NamedTextColor.YELLOW))
                    }
                }
            }
        }.runTaskLater(plugin, 12000L) // 10분
    }
}
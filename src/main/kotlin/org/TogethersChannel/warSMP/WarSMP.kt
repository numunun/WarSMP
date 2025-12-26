package org.TogethersChannel.warSMP

import org.TogethersChannel.betterTeaming.api.BetterTeamingAPI
import org.TogethersChannel.warSMP.commands.*
import org.TogethersChannel.warSMP.game.GameManager
import org.TogethersChannel.warSMP.listeners.*
import org.TogethersChannel.warSMP.systems.*
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class WarSMP : JavaPlugin() {
    lateinit var api: BetterTeamingAPI; private set

    var isWarStarted = false
    var isGameRunning = false
    var isPeaceTime = false
    var peaceTimeSeconds = 180
    var pvpKeepInventory = false
    var scatterRadius = -1

    val lastAttacker = mutableMapOf<UUID, Pair<UUID, Long>>()

    lateinit var gameManager: GameManager
    // [수정] 외부에서 접근 가능하도록 nullable로 변경
    var beaconListener: BeaconListener? = null
    var proximityDetector: ProximityDetector? = null

    interface WarAPI { fun isWarStarted(): Boolean }

    override fun onEnable() {
        if (!setupAPI()) { server.pluginManager.disablePlugin(this); return }

        // 순서대로 초기화
        val bListener = BeaconListener(this)
        beaconListener = bListener

        gameManager = GameManager(this)
        proximityDetector = ProximityDetector(this)

        server.pluginManager.registerEvents(PlayerDeathListener(this), this)
        server.pluginManager.registerEvents(ItemBanListener(this), this)
        server.pluginManager.registerEvents(GameRuleListener(this), this)
        server.pluginManager.registerEvents(bListener, this)
        server.pluginManager.registerEvents(WorldBanListener(this), this)

        getCommand("warsmp")?.setExecutor(WarSMPCommand(this))
        getCommand("tptochief")?.setExecutor(TpToChiefCommand(this))

        server.servicesManager.register(WarAPI::class.java, object : WarAPI {
            override fun isWarStarted() = this@WarSMP.isWarStarted
        }, this, ServicePriority.Normal)

        logger.info("WarSMP 활성화됨")
    }

    override fun onDisable() {
        // gameManager가 초기화되었을 때만 중지 호출
        if (::gameManager.isInitialized) gameManager.stopGame(false)
        logger.info("WarSMP 비활성화됨")
    }

    private fun setupAPI(): Boolean {
        api = server.servicesManager.getRegistration(BetterTeamingAPI::class.java)?.provider ?: return false
        return true
    }
}
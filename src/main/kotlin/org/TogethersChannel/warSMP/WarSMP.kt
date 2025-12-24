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
    val slaves = mutableMapOf<UUID, String>()
    var isGameRunning = false
    var isPeaceTime = false
    var peaceTimeSeconds = 180
    var pvpKeepInventory = false
    var scatterRadius = -1

    // [신규] 전투 로그 (맞은사람UUID -> <때린사람UUID, 맞은시간>)
    val lastAttacker = mutableMapOf<UUID, Pair<UUID, Long>>()

    lateinit var gameManager: GameManager
    lateinit var beaconListener: BeaconListener
    lateinit var proximityDetector: ProximityDetector
    lateinit var slaveEffectTask: SlaveEffectTask

    interface WarAPI { fun isWarStarted(): Boolean }

    override fun onEnable() {
        if (!setupAPI()) { server.pluginManager.disablePlugin(this); return }

        slaveEffectTask = SlaveEffectTask(this).apply { runTaskTimer(this@WarSMP, 0L, 100L) }
        gameManager = GameManager(this)
        beaconListener = BeaconListener(this)
        proximityDetector = ProximityDetector(this)

        server.pluginManager.registerEvents(PlayerDeathListener(this), this)
        server.pluginManager.registerEvents(ItemBanListener(this), this)
        server.pluginManager.registerEvents(GameRuleListener(this), this)
        server.pluginManager.registerEvents(beaconListener, this)
        server.pluginManager.registerEvents(WorldBanListener(this), this)
        server.pluginManager.registerEvents(SlaveListener(this), this)

        getCommand("warsmp")?.setExecutor(WarSMPCommand(this))
        getCommand("tptochief")?.setExecutor(TpToChiefCommand(this))

        server.servicesManager.register(WarAPI::class.java, object : WarAPI {
            override fun isWarStarted() = this@WarSMP.isWarStarted
        }, this, ServicePriority.Normal)

        logger.info("WarSMP 활성화됨")
    }

    override fun onDisable() {
        if (::gameManager.isInitialized) gameManager.stopGame(false)
        if (::slaveEffectTask.isInitialized) slaveEffectTask.cancel()
        logger.info("WarSMP 비활성화됨")
    }

    private fun setupAPI(): Boolean {
        api = server.servicesManager.getRegistration(BetterTeamingAPI::class.java)?.provider ?: return false
        return true
    }
}
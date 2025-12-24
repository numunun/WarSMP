package org.TogethersChannel.warSMP.systems

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

class SlaveEffectTask(private val plugin: WarSMP) : BukkitRunnable() {

    private val slaveAttributeModifier = AttributeModifier(
        UUID.fromString("e61a3-b0b-4a5-b1e-23f4f1f51"), // 고유 ID
        "SlaveHealthPenalty",
        -10.0, // 체력 5칸 (10.0) 감소
        AttributeModifier.Operation.ADD_NUMBER
    )

    override fun run() {
        if (!plugin.isGameRunning || !plugin.isWarStarted) {
            return
        }

        // 온라인 상태인 노예들에게만 효과 적용
        plugin.slaves.keys.forEach { slaveUUID ->
            val player = plugin.server.getPlayer(slaveUUID)
            if (player != null && player.isOnline) {
                // 나약함 2 상시 적용 (10초 지속, 5초마다 갱신)
                player.addPotionEffect(
                    PotionEffect(PotionEffectType.WEAKNESS, 200, 1, false, false) // 10초(200틱), 레벨 2 (0부터 시작하므로 1)
                )
            }
        }
    }

    /**
     * 플레이어에게 노예 효과(최대 체력 감소)를 적용합니다.
     * (리스폰 또는 접속 시 호출됨)
     */
    fun applySlaveEffects(player: Player) {
        val healthAttribute = player.getAttribute(Attribute.MAX_HEALTH)

        // 중복 적용 방지
        if (healthAttribute?.modifiers?.none { it.name == slaveAttributeModifier.name } == true) {
            healthAttribute.addModifier(slaveAttributeModifier)
            player.health = player.health.coerceAtMost(10.0) // 현재 체력도 10으로 강제
        }
    }

    /**
     * 플레이어의 노예 효과(최대 체력 감소)를 제거합니다.
     * (현재 기획상 노예 해방은 없지만, 게임 종료 시 호출)
     */
    fun removeSlaveEffects(player: Player) {
        val healthAttribute = player.getAttribute(Attribute.MAX_HEALTH)
        healthAttribute?.modifiers?.forEach {
            if (it.name == slaveAttributeModifier.name) {
                healthAttribute.removeModifier(it)
            }
        }
    }

    /**
     * 게임 종료 시 모든 노예 효과를 초기화합니다.
     */
    fun clearSlaves() {
        plugin.slaves.keys.forEach { uuid ->
            plugin.server.getPlayer(uuid)?.let { removeSlaveEffects(it) }
        }
    }
}
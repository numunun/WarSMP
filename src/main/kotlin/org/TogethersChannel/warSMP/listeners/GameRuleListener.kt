package org.TogethersChannel.warSMP.listeners

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

class GameRuleListener(private val plugin: WarSMP) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDamage(event: EntityDamageByEntityEvent) {
        if (!plugin.isGameRunning) return

        if (event.entity is Player) {
            val victim = event.entity as Player
            var attacker: Player? = null

            if (event.damager is Player) {
                attacker = event.damager as Player
            } else if (event.damager is Projectile) {
                val shooter = (event.damager as Projectile).shooter
                if (shooter is Player) {
                    attacker = shooter
                }
            }

            // [신규] 평화 시간 체크 및 전투 로그 기록
            if (attacker != null) {
                if (plugin.isPeaceTime) {
                    event.isCancelled = true
                    attacker.sendActionBar(Component.text("아직 평화 시간입니다.", NamedTextColor.YELLOW))
                } else {
                    // 전투 로그 저장 (맞은 시간 기록)
                    plugin.lastAttacker[victim.uniqueId] = Pair(attacker.uniqueId, System.currentTimeMillis())
                }
            }
        }
    }
}
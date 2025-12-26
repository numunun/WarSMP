package org.TogethersChannel.warSMP.listeners

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.TogethersChannel.warSMP.WarSMP
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.enchantment.PrepareItemEnchantEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.inventory.PrepareSmithingEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable

class CustomItemListener(private val plugin: WarSMP) : Listener {

    init {
        // 신발 효과 부여 태스크 (1초마다 체크)
        object : BukkitRunnable() {
            override fun run() {
                plugin.server.onlinePlayers.forEach { player ->
                    val boots = player.inventory.boots ?: return@forEach
                    val name = getDisplayName(boots)

                    when {
                        name == "신속점프강화신" -> {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 40, 0, false, false))
                            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 40, 0, false, false))
                        }
                        name == "신속신" -> {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 40, 0, false, false))
                        }
                        name == "점프강화신" -> {
                            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 40, 0, false, false))
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    private fun getDisplayName(item: ItemStack?): String {
        if (item == null || !item.hasItemMeta()) return ""
        val comp = item.itemMeta.displayName() ?: return ""
        return PlainTextComponentSerializer.plainText().serialize(comp)
    }

    // [2] 불사의 토템 몹 드랍 방지
    @EventHandler
    fun onEntityDeath(e: EntityDeathEvent) {
        if (e.entityType == EntityType.EVOKER) {
            e.drops.removeIf { it.type == Material.TOTEM_OF_UNDYING }
        }
    }

    // [3, 4, 5] 인챈트 불가
    @EventHandler
    fun onPrepareEnchant(e: PrepareItemEnchantEvent) {
        val name = getDisplayName(e.item)
        if (isCustomBoots(name)) e.isCancelled = true
    }

    // [3, 4, 5] 네더라이트 진화 불가 (대장장이 작업대)
    @EventHandler
    fun onPrepareSmithing(e: PrepareSmithingEvent) {
        val input = e.inventory.getItem(0) ?: return
        if (isCustomBoots(getDisplayName(input))) {
            e.result = null
        }
    }

    // [5] 신속점프강화신 제작 시 재료가 일반 신발이 아닌 '커스텀 신발'인지 검증
    @EventHandler
    fun onPrepareCraft(e: PrepareItemCraftEvent) {
        val result = e.recipe?.result ?: return
        val resultName = getDisplayName(result)

        if (resultName == "신속점프강화신") {
            val matrix = e.inventory.matrix
            val leftItem = matrix[3]  // 신속신 위치
            val rightItem = matrix[5] // 점프신 위치

            if (getDisplayName(leftItem) != "신속신" || getDisplayName(rightItem) != "점프강화신") {
                e.inventory.result = null
            }
        }

        // 일반 다이아 신발에 이름만 붙여서 커스텀 신발인 척 하는 것 방지
        if (resultName == "신속신" || resultName == "점프강화신") {
            val baseBoots = e.inventory.matrix.firstOrNull { it?.type == Material.DIAMOND_BOOTS }
            // 재료 신발에 이미 이름(NBT)이 있다면 제작 불가 (기초 다이아 신발만 허용)
            if (baseBoots != null && baseBoots.hasItemMeta() && baseBoots.itemMeta.hasDisplayName()) {
                e.inventory.result = null
            }
        }
    }

    private fun isCustomBoots(name: String): Boolean {
        return name == "신속신" || name == "점프강화신" || name == "신속점프강화신"
    }
}
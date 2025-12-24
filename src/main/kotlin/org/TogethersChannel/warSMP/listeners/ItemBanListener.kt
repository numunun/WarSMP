package org.TogethersChannel.warSMP.listeners

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.ChunkLoadEvent

class ItemBanListener(private val plugin: WarSMP) : Listener {

    // ... (banned item 목록은 생략) ...
    private val bannedInteract = setOf(Material.MACE, Material.TOTEM_OF_UNDYING, Material.ENDER_PEARL)
    private val bannedEquip = setOf(Material.ELYTRA)
    private val bannedPlace = setOf(Material.RESPAWN_ANCHOR, Material.END_CRYSTAL)
    private val bannedCraft = setOf(Material.END_CRYSTAL)


    private fun notifyBan(player: Player, item: String) {
        // [수정] Component.text 오류 수정
        player.sendActionBar(Component.text("[$item] 아이템은 금지되었습니다.", NamedTextColor.RED))
    }

    // ... (이하 이벤트 핸들러는 notifyBan을 호출하므로 자동으로 수정됨, 코드는 생략) ...
    @EventHandler
    fun onCraft(event: CraftItemEvent) {
        if (!plugin.isGameRunning) return
        if (event.recipe.result.type in bannedCraft) {
            event.isCancelled = true
            notifyBan(event.whoClicked as Player, event.recipe.result.type.name)
        }
    }

    @EventHandler
    fun onPlace(event: BlockPlaceEvent) {
        if (!plugin.isGameRunning) return
        if (event.block.type in bannedPlace) {
            event.isCancelled = true
            notifyBan(event.player, event.block.type.name)
        }
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (!plugin.isGameRunning) return
        val item = event.item ?: return

        if (item.type in bannedInteract) {
            event.isCancelled = true
            notifyBan(event.player, item.type.name)
        }

        if (event.player.inventory.itemInOffHand.type == Material.TOTEM_OF_UNDYING) {
            event.isCancelled = true
            notifyBan(event.player, Material.TOTEM_OF_UNDYING.name)
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (!plugin.isGameRunning) return

        if (event.slotType == InventoryType.SlotType.ARMOR && event.cursor?.type == Material.ELYTRA) {
            event.isCancelled = true
            notifyBan(event.whoClicked as Player, Material.ELYTRA.name)
        }
        if (event.click.isShiftClick && event.currentItem?.type == Material.ELYTRA) {
            if (event.inventory.type == InventoryType.CRAFTING || event.inventory.type == InventoryType.PLAYER) {
                event.isCancelled = true
                notifyBan(event.whoClicked as Player, Material.ELYTRA.name)
            }
        }
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!plugin.isGameRunning) return
        if (event.world.environment.name == "THE_END") {
            for (entity in event.chunk.entities) {
                if (entity is EnderCrystal) {
                    entity.remove()
                }
            }
        }
    }
}
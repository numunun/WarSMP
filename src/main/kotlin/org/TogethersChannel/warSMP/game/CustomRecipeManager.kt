package org.TogethersChannel.warSMP.game

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.TogethersChannel.warSMP.WarSMP
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe

class CustomRecipeManager(private val plugin: WarSMP) {

    fun registerAllRecipes() {
        registerReviveTicketRecipe() // 1. 소생권
        registerTotemRecipe()        // 2. 불사의 토템
        registerSpeedBootsRecipe()   // 3. 신속신
        registerJumpBootsRecipe()    // 4. 점프강화신
        registerHybridBootsRecipe()  // 5. 신속점프강화신
    }

    // 1. 소생권 (기존 로직 유지 또는 새로 정의)
    private fun registerReviveTicketRecipe() {
        val item = ItemStack(Material.PAPER).apply {
            val meta = itemMeta
            meta.displayName(Component.text("소생권", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(Component.text("죽은 팀원을 부활시킬 때 사용합니다.", NamedTextColor.GRAY)))
            itemMeta = meta
        }
        val recipe = ShapedRecipe(NamespacedKey(plugin, "revive_ticket"), item)
        recipe.shape("NGN", "ISD", "NCN")
        recipe.setIngredient('G', Material.GOLD_BLOCK)
        recipe.setIngredient('N', Material.NETHERITE_INGOT)
        recipe.setIngredient('I', Material.IRON_BLOCK)
        recipe.setIngredient('D', Material.DIAMOND_BLOCK)
        recipe.setIngredient('C', Material.COPPER_BLOCK)
        recipe.setIngredient('S', Material.NETHER_STAR)
        plugin.server.addRecipe(recipe)
    }

    // 2. 불사의 토템
    private fun registerTotemRecipe() {
        val item = ItemStack(Material.TOTEM_OF_UNDYING)
        val recipe = ShapedRecipe(NamespacedKey(plugin, "custom_totem"), item)
        recipe.shape(" N ", "EGE", " G ")
        recipe.setIngredient('N', Material.NETHERITE_BLOCK)
        recipe.setIngredient('E', Material.EMERALD_BLOCK)
        recipe.setIngredient('G', Material.GOLD_BLOCK)
        plugin.server.addRecipe(recipe)
    }

    // 3. 신속신 생성 함수
    fun getSpeedBoots(): ItemStack {
        return ItemStack(Material.DIAMOND_BOOTS).apply {
            val meta = itemMeta
            meta.displayName(Component.text("신속신", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(Component.text("착용 시 신속 I 효과를 부여합니다.", NamedTextColor.GRAY)))
            meta.isUnbreakable = true
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES)
            itemMeta = meta
        }
    }

    private fun registerSpeedBootsRecipe() {
        val recipe = ShapedRecipe(NamespacedKey(plugin, "speed_boots"), getSpeedBoots())
        recipe.shape("   ", "FBF", "   ")
        recipe.setIngredient('F', Material.FEATHER)
        recipe.setIngredient('B', Material.DIAMOND_BOOTS)
        plugin.server.addRecipe(recipe)
    }

    // 4. 점프강화신 생성 함수
    fun getJumpBoots(): ItemStack {
        return ItemStack(Material.DIAMOND_BOOTS).apply {
            val meta = itemMeta
            meta.displayName(Component.text("점프강화신", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(Component.text("착용 시 점프 강화 I 효과를 부여합니다.", NamedTextColor.GRAY)))
            meta.isUnbreakable = true
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES)
            itemMeta = meta
        }
    }

    private fun registerJumpBootsRecipe() {
        val recipe = ShapedRecipe(NamespacedKey(plugin, "jump_boots"), getJumpBoots())
        recipe.shape("   ", "RBR", "   ")
        recipe.setIngredient('R', Material.RABBIT_FOOT)
        recipe.setIngredient('B', Material.DIAMOND_BOOTS)
        plugin.server.addRecipe(recipe)
    }

    // 5. 신속점프강화신 생성 함수
    fun getHybridBoots(): ItemStack {
        return ItemStack(Material.DIAMOND_BOOTS).apply {
            val meta = itemMeta
            meta.displayName(Component.text("신속점프강화신", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(Component.text("착용 시 신속 I 및 점프 강화 I 효과를 부여합니다.", NamedTextColor.GRAY)))
            meta.isUnbreakable = true
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES)
            itemMeta = meta
        }
    }

    private fun registerHybridBootsRecipe() {
        val recipe = ShapedRecipe(NamespacedKey(plugin, "hybrid_boots"), getHybridBoots())
        recipe.shape("   ", "SDR", "   ")
        recipe.setIngredient('S', Material.DIAMOND_BOOTS) // 제작대 매트릭스 체크에서 커스텀 신발인지 확인 예정
        recipe.setIngredient('D', Material.DIAMOND_BLOCK)
        recipe.setIngredient('R', Material.DIAMOND_BOOTS)
        plugin.server.addRecipe(recipe)
    }
}
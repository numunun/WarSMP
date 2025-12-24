package org.TogethersChannel.warSMP.commands

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.*

class WarSMPCommand(private val plugin: WarSMP) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("warsmp.admin")) return true
        if (args.isEmpty()) { sendHelp(sender); return true }

        when (args[0].lowercase()) {
            "start" -> { plugin.gameManager.startGame(); sender.sendMessage(Component.text("게임 시작", NamedTextColor.GREEN)) }
            "forcestop" -> { plugin.gameManager.stopGame(true); sender.sendMessage(Component.text("강제 종료", NamedTextColor.RED)) }
            "warstart" -> {
                if (!plugin.isGameRunning) {
                    sender.sendMessage(Component.text("먼저 /warsmp start로 게임을 시작해야 합니다.", NamedTextColor.RED))
                    return true
                }
                if (plugin.isWarStarted) {
                    sender.sendMessage(Component.text("전쟁은 이미 시작되었습니다.", NamedTextColor.YELLOW))
                    return true
                }

                // [신규] 팀 없는 플레이어 검사
                val noTeamPlayers = plugin.server.onlinePlayers.filter { plugin.api.getPlayerTeamName(it) == null }
                if (noTeamPlayers.isNotEmpty()) {
                    sender.sendMessage(Component.text("다음 플레이어들이 팀에 소속되어 있지 않아 전쟁을 시작할 수 없습니다:", NamedTextColor.RED))
                    noTeamPlayers.forEach {
                        sender.sendMessage(Component.text("- ${it.name}", NamedTextColor.YELLOW))
                    }
                    return true
                }

                plugin.isWarStarted = true
                plugin.server.broadcast(
                    Component.text("============== [ 전쟁 시작 ] ==============", NamedTextColor.RED, TextDecoration.BOLD)
                )
                plugin.server.broadcast(
                    Component.text("지금부터 신호기 파괴가 가능해지며, 사망 시 아이템 드롭 규칙이 적용됩니다.", NamedTextColor.YELLOW)
                )
                plugin.server.broadcast(
                    Component.text("============== [ 전쟁 시작 ] ==============", NamedTextColor.RED, TextDecoration.BOLD)
                )
            }
            "config" -> handleConfig(sender, args)
            else -> sendHelp(sender)
        }
        return true
    }

    private fun handleConfig(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) { sender.sendMessage("사용법 확인"); return }
        when (args[1].lowercase()) {
            "peacekeeper" -> plugin.peaceTimeSeconds = args[2].toInt()
            "keepinventory" -> plugin.pvpKeepInventory = args[2].toBoolean()
            "scatter" -> {
                plugin.scatterRadius = args[2].toInt()
                sender.sendMessage(Component.text("스폰 반경: ${if(plugin.scatterRadius==-1) "자동" else plugin.scatterRadius}", NamedTextColor.GREEN))
            }
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("/warsmp start / forcestop / warstart / config ...")
    }

    override fun onTabComplete(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): MutableList<String>? {
        if (!sender.hasPermission("warsmp.admin")) return null
        if (args.size == 1) return mutableListOf("start", "forcestop", "config", "warstart").filter { it.startsWith(args[0], true) }.toMutableList()
        if (args.size == 2 && args[0].equals("config", true)) return mutableListOf("peacekeeper", "keepinventory", "scatter")
        return mutableListOf()
    }
}
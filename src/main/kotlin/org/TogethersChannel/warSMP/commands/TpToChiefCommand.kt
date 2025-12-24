package org.TogethersChannel.warSMP.commands

import org.TogethersChannel.warSMP.WarSMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TpToChiefCommand(private val plugin: WarSMP) : CommandExecutor {

    private val api = plugin.api

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.")
            return true
        }

        if (!plugin.isGameRunning) {
            // [수정] Component.text 오류 수정
            sender.sendMessage(Component.text("게임이 실행 중이 아닙니다.", NamedTextColor.RED))
            return true
        }

        val teamName = plugin.beaconListener.tpCooldown[sender.uniqueId]
        if (teamName == null) {
            // [수정] Component.text 오류 수정
            sender.sendMessage(Component.text("지금은 사용할 수 없습니다. (팀 재건 후 10분간 유효)", NamedTextColor.RED))
            return true
        }

        val leaderUUID = api.getTeamLeader(teamName)
        if (leaderUUID == null) {
            // [수정] Component.text 오류 수정
            sender.sendMessage(Component.text("팀장을 찾을 수 없습니다.", NamedTextColor.RED))
            return true
        }

        val leader = plugin.server.getPlayer(leaderUUID)
        if (leader == null || !leader.isOnline) {
            // [수정] Component.text 오류 수정
            sender.sendMessage(Component.text("팀장이 오프라인 상태입니다.", NamedTextColor.RED))
            return true
        }

        sender.teleport(leader.location)
        // [수정] Component.text 오류 수정
        sender.sendMessage(Component.text("팀장에게 텔레포트합니다!", NamedTextColor.GREEN))

        plugin.beaconListener.tpCooldown.remove(sender.uniqueId)
        return true
    }
}
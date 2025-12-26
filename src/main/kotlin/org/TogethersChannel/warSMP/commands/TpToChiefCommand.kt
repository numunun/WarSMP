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
            sender.sendMessage(Component.text("게임이 실행 중이 아닙니다.", NamedTextColor.RED))
            return true
        }

        // [수정] ?. 을 사용하여 Null 체크 처리
        val teamName = plugin.beaconListener?.tpCooldown?.get(sender.uniqueId)

        if (teamName == null) {
            sender.sendMessage(Component.text("지금은 사용할 수 없습니다. (팀 재건 후 10분간 유효)", NamedTextColor.RED))
            return true
        }

        val leaderUUID = api.getTeamLeader(teamName)
        if (leaderUUID == null) {
            sender.sendMessage(Component.text("팀장을 찾을 수 없습니다.", NamedTextColor.RED))
            return true
        }

        val leader = plugin.server.getPlayer(leaderUUID)
        if (leader == null || !leader.isOnline) {
            sender.sendMessage(Component.text("팀장이 오프라인 상태입니다.", NamedTextColor.RED))
            return true
        }

        sender.teleport(leader.location)
        sender.sendMessage(Component.text("팀장에게 텔레포트합니다!", NamedTextColor.GREEN))

        // [수정] ?. 을 사용하여 안전하게 제거
        plugin.beaconListener?.tpCooldown?.remove(sender.uniqueId)
        return true
    }
}
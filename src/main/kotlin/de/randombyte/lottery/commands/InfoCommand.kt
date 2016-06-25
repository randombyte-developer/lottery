package de.randombyte.lottery.commands

import de.randombyte.lottery.ConfigManager
import de.randombyte.lottery.Lottery
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.format.TextColors

class InfoCommand : PlayerCommandExecutor() {
    override fun executedByPlayer(player: Player, args: CommandContext): CommandResult {
        val config = ConfigManager.loadConfig()
        player.sendMessage(Text.of(
                TextColors.GRAY,
                "You currently have ${config.internalData.boughtTickets[player.uniqueId] ?: 0} tickets(s) " +
                        "and there are ${Lottery.getPot(config)}$ in the pot, the draw is in "))
        return CommandResult.success()
    }
}
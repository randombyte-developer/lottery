package de.randombyte.lottery.commands

import de.randombyte.kosp.PlayerExecutedCommand
import de.randombyte.kosp.config.ConfigManager
import de.randombyte.lottery.Config
import de.randombyte.lottery.Lottery
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.format.TextColors
import java.time.Duration

class InfoCommand(
        val configManager: ConfigManager<Config>,
        val data: (currentPot: Int, defaultCurrency: Currency, durationUntilDraw: Duration) -> Unit
) : PlayerExecutedCommand() {
    override fun executedByPlayer(player: Player, args: CommandContext): CommandResult {
        val config = configManager.get()
        player.sendMessage(Text.builder()
                .append(Text.of(TextColors.GRAY, "You currently have "))
                .append(Text.of(TextColors.AQUA, "${config.internalData.getBoughtTickets(player)} tickets(s) "))
                .append(Text.of(TextColors.GRAY, "and there are "))
                .append(Text.of(TextColors.AQUA, Lottery.getPot(config)))
                .append(Lottery.getEconomyServiceOrFail().defaultCurrency.symbol)
                .append(Text.of(TextColors.GRAY, " in the pot. "))
                .append(Text.of(TextColors.AQUA, "Buy a ticket with /lottery buy! "))
                .append(Text.of(TextColors.GRAY, "The next draw is in "))
                .append(Text.of(TextColors.AQUA, "${Lottery.getDurationUntilDraw().toMinutes()} minutes."))
                .build()
        )
        return CommandResult.success()
    }
}
package de.randombyte.lottery.commands

import de.randombyte.kosp.PlayerExecutedCommand
import de.randombyte.kosp.config.ConfigManager
import de.randombyte.lottery.Config
import de.randombyte.lottery.getDefaultCurrency
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.entity.living.player.Player
import java.time.Duration

class InfoCommand(
        val configManager: ConfigManager<Config>,
        val durationUntilDraw: () -> Duration
) : PlayerExecutedCommand() {
    override fun executedByPlayer(player: Player, args: CommandContext): CommandResult {
        val config = configManager.get()

        val currency = getDefaultCurrency()

        val infoText = config.messages.infoMessage.apply(mapOf(
                "boughtTickets" to config.internalData.getBoughtTickets(player),
                "pot" to config.calculatePot(),
                "currencySymbol" to currency.symbol,
                "currencyName" to currency.name,
                "minutesUntilDraw" to durationUntilDraw().toMinutes()
        )).build()
        player.sendMessage(infoText)

        return CommandResult.success()
    }
}
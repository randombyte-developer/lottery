package de.randombyte.lottery.commands

import de.randombyte.kosp.config.ConfigManager
import de.randombyte.lottery.Config
import de.randombyte.lottery.getDefaultCurrency
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.command.spec.CommandExecutor
import org.spongepowered.api.entity.living.player.Player
import java.time.Duration

class InfoCommand(
        val configManager: ConfigManager<Config>,
        val durationUntilDraw: () -> Duration
) : CommandExecutor {
    override fun execute(src: CommandSource, args: CommandContext): CommandResult {
        val config = configManager.get()
        val currency = getDefaultCurrency()

        if (src is Player) {
            val infoText = config.messages.infoMessagePlayer.apply(mapOf(
                    "boughtTickets" to config.internalData.getBoughtTickets(src),
                    "pot" to config.calculatePot(),
                    "currencySymbol" to currency.symbol,
                    "currencyName" to currency.name,
                    "minutesUntilDraw" to durationUntilDraw().toMinutes(),
                    "ticketCosts" to config.ticketCosts
            )).build()
            src.sendMessage(infoText)
        } else {
            val infoText = config.messages.infoMessageConsole.apply(mapOf(
                    "pot" to config.calculatePot(),
                    "currencySymbol" to currency.symbol,
                    "currencyName" to currency.name,
                    "minutesUntilDraw" to durationUntilDraw().toMinutes(),
                    "ticketCosts" to config.ticketCosts
            )).build()
            src.sendMessage(infoText)
        }

        return CommandResult.success()
    }
}
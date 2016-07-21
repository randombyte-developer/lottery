package de.randombyte.lottery.commands

import de.randombyte.lottery.ConfigManager
import de.randombyte.lottery.Lottery
import org.spongepowered.api.Sponge
import org.spongepowered.api.command.CommandException
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.service.economy.transaction.ResultType
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.action.TextActions
import org.spongepowered.api.text.format.TextColors
import java.math.BigDecimal

class BuyTicketCommand : PlayerCommandExecutor() {
    override fun executedByPlayer(player: Player, args: CommandContext): CommandResult {
        val config = ConfigManager.loadConfig()

        val boughtTickets = config.internalData.boughtTickets[player.uniqueId] ?: 0
        val amount = args.getOne<Int>("ticketAmount").orElse(1)
        val ticketCosts = config.ticketCosts * amount
        val finalBoughtTickets = boughtTickets + amount

        val newConfig = if (config.maxTickets >= finalBoughtTickets) {
            config.copy(internalData = config.internalData.copy(
                    boughtTickets = config.internalData.boughtTickets + (player.uniqueId to finalBoughtTickets),
                    pot = config.internalData.pot + amount * config.ticketCosts))
        } else {
            throw CommandException(Text.of("Maximum tickets per player reached! " +
                    "You can buy ${config.maxTickets - config.internalData.boughtTickets[player.uniqueId]!!} tickets!"))
        }

        val economyService = Lottery.getEconomyServiceOrFail()
        val transactionResult = economyService.getOrCreateAccount(player.uniqueId).get()
                .withdraw(economyService.defaultCurrency, BigDecimal(ticketCosts), Lottery.PLUGIN_CAUSE)
        if (!transactionResult.result.equals(ResultType.SUCCESS)) {
            throw CommandException(Text.of("Transaction failed!"))
        }

        ConfigManager.saveConfig(newConfig)

        player.sendMessage(Text.of(TextColors.GRAY,
                "You bought $amount tickets(s) and now have a total amount of $finalBoughtTickets tickets(s)!"))

        if (ConfigManager.loadConfig().broadcastTicketPurchase) {
            Sponge.getServer().broadcastChannel.send(Text.builder()
                    .append(Text.of(TextColors.GOLD, "${player.name} has bought $amount ticket(s)! "))
                    .append(Text.builder("/lottery info").color(TextColors.AQUA).
                            onClick(TextActions.suggestCommand("/lottery info")).build())
                    .build())
        }

        return CommandResult.success()
    }
}
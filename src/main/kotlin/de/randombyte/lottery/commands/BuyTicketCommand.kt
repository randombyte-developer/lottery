package de.randombyte.lottery.commands

import de.randombyte.kosp.PlayerExecutedCommand
import de.randombyte.kosp.config.ConfigManager
import de.randombyte.kosp.extensions.red
import de.randombyte.lottery.Config
import de.randombyte.lottery.broadcast
import de.randombyte.lottery.getEconomyServiceOrFail
import org.spongepowered.api.command.CommandException
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.service.economy.transaction.ResultType
import org.spongepowered.api.text.Text
import java.math.BigDecimal

class BuyTicketCommand(
        val configManager : ConfigManager<Config>,
        val transactionCause: Cause
) : PlayerExecutedCommand() {
    override fun executedByPlayer(player: Player, args: CommandContext): CommandResult {
        val config = configManager.get()

        val boughtTickets = config.internalData.getBoughtTickets(player)
        val amount = args.getOne<Int>("ticketAmount").orElse(1)
        if (amount < 1) throw CommandException("'ticketAmount' must be positive!".red())
        val ticketCosts = config.ticketCosts * amount
        val finalBoughtTickets = boughtTickets + amount

        val newConfig = if (config.maxTickets >= finalBoughtTickets) {
            config.copy(internalData = config.internalData.copy(
                    boughtTickets = config.internalData.boughtTickets + (player.uniqueId to finalBoughtTickets),
                    pot = config.internalData.pot + ticketCosts))
        } else {
            val errorTextBuilder = Text.builder("Maximum tickets per player reached!")
            val ticketsAvailableForBuy = config.maxTickets - boughtTickets
            if (ticketsAvailableForBuy > 0) {
                errorTextBuilder.append(Text.of(" You can only buy $ticketsAvailableForBuy more ticket(s)!"))
            }
            throw CommandException(errorTextBuilder.build())
        }

        val economyService = getEconomyServiceOrFail()
        val account = economyService.getOrCreateAccount(player.uniqueId).get()
        val costs = BigDecimal(ticketCosts)

        if (account.getBalance(economyService.defaultCurrency) < costs) throw CommandException(config.messages.notEnoughMoney)
        val transactionResult = account.withdraw(economyService.defaultCurrency, costs, transactionCause)
        if (transactionResult.result != ResultType.SUCCESS) throw CommandException("Transaction failed!".red())

        configManager.save(newConfig)

        val buyMessage = config.messages.buyTicketMessage.apply(mapOf(
                "boughtTickets" to amount,
                "totalTickets" to finalBoughtTickets)
        ).build()
        player.sendMessage(buyMessage)

        if (config.broadcasts.broadcastTicketPurchase) {
            val broadcastText = config.messages.buyTicketBroadcast.apply(mapOf(
                    "buyerName" to player.name,
                    "ticketAmount" to amount,
                    "pot" to config.calculatePot()
            )).build()
            broadcast(broadcastText)
        }

        return CommandResult.success()
    }
}
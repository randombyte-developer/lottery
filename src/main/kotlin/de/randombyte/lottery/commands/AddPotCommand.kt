package de.randombyte.lottery.commands

import de.randombyte.kosp.config.ConfigManager
import de.randombyte.kosp.extensions.green
import de.randombyte.kosp.extensions.plus
import de.randombyte.kosp.extensions.toText
import de.randombyte.lottery.Config
import de.randombyte.lottery.getEconomyServiceOrFail
import org.spongepowered.api.command.CommandException
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.command.spec.CommandExecutor
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.service.economy.transaction.ResultType
import java.math.BigDecimal

class AddPotCommand(
        val configManager: ConfigManager<Config>,
        val transactionCause: Cause
) : CommandExecutor {
    override fun execute(src: CommandSource, args: CommandContext): CommandResult {
        val config = configManager.get()
        val amount = args.getOne<Int>("amount").get()
        val maxDeposit = config.maxDeposit

        if (amount < 1) throw CommandException("'amount' has to be a value above zero!".toText())
        if (amount > maxDeposit || !src.hasPermission("lottery.admin")) {
            throw CommandException("You're not allowed to deposit amounts above $maxDeposit!".toText())
        }

        val economyService = getEconomyServiceOrFail()

        if (src is Player) {
            val transactionResult = economyService.getOrCreateAccount(src.uniqueId).get()
                    .withdraw(economyService.defaultCurrency, BigDecimal(amount), transactionCause)
            if (transactionResult.result != ResultType.SUCCESS) {
                throw CommandException(config.messages.notEnoughMoney)
            }
        }

        val newConfig = config.copy(internalData = config.internalData.copy(pot = config.internalData.pot + amount))
        configManager.save(newConfig)
        val amountText = economyService.defaultCurrency.format(BigDecimal(amount))
        src.sendMessage(amountText + " has been added to the pot.".green())
        return CommandResult.success()
    }
}
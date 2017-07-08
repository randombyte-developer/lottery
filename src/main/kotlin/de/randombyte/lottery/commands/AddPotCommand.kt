package de.randombyte.lottery.commands

import de.randombyte.kosp.config.ConfigManager
import de.randombyte.kosp.extensions.green
import de.randombyte.kosp.extensions.red
import de.randombyte.lottery.Config
import org.spongepowered.api.text.format.TextColors
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
        val configManager : ConfigManager<Config>,
        val transactionCause: Cause
) : CommandExecutor {
    override fun execute(src: CommandSource, args: CommandContext): CommandResult {
        val config = configManager.get()
        if (src is Player) {
            val amount = args.getOne<Int>("addpotAmount").orElse(0)
            val maxDeposit = config.maxDeposit
            val addpotConfig = if (amount >= 0) {
                if (!src.hasPermission("lottery.admin") && amount <= maxDeposit) {
                    config.copy(internalData = config.internalData.copy(
                            pot = config.internalData.pot + amount))
                }
                else if (src.hasPermission("lottery.admin")) {
                    config.copy(internalData = config.internalData.copy(
                            pot = config.internalData.pot + amount))
                } else { throw CommandException("You're not allowed to deposit amounts above $maxDeposit".red()) }
            } else { throw CommandException("Amount has to be a value above 0".red()) }
            val economyService = getEconomyServiceOrFail()
            val transactionResult = economyService.getOrCreateAccount(src.uniqueId).get()
                    .withdraw(economyService.defaultCurrency, BigDecimal(amount), transactionCause)
            if (transactionResult.result != ResultType.SUCCESS) {
                throw CommandException("You do not have enough money to do that.".red())
            }
            configManager.save(addpotConfig)
            src.sendMessage("$amount has been added to the pot".green())
        } else {
            val amount = args.getOne<Int>("addpotAmount").orElse(0)
            val addpotConfigConsole = if (amount >= 0) {
                config.copy(internalData = config.internalData.copy(
                        pot = config.internalData.pot + amount))
            } else { throw CommandException("Could not add $amount to the pot".red()) }
            configManager.save(addpotConfigConsole)
            src.sendMessage("$amount has been added to the pot".green())
        }
        return CommandResult.success()
    }
}
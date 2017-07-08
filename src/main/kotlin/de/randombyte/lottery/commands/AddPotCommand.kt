package de.randombyte.lottery.commands

import de.randombyte.kosp.config.ConfigManager
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
import org.spongepowered.api.text.Text
import java.math.BigDecimal

class AddPotCommand(
        val configManager : ConfigManager<Config>,
        val transactionCause: Cause
) : CommandExecutor {
    override fun execute(src: CommandSource, args: CommandContext): CommandResult {
        val config = configManager.get()

        if (src is Player) {

            val amount = args.getOne<Int>("addpotAmount").orElse(0)
            val takeAmount = amount
            val DisplayAmount = Integer.toString(amount)
            val addPotText = Text.builder("The following amount has been added to the pot: ").build()
            val addPotValue = Text.builder(DisplayAmount).color(TextColors.GREEN).build()
            val addpotConfig = if (amount >= 0) {
                config.copy(internalData = config.internalData.copy(
                        pot = config.internalData.pot + amount))
            } else {
                val errorTextBuilder = Text.builder("Error: Could not add to the pot").color(TextColors.DARK_RED).build()
                throw CommandException(errorTextBuilder)
            }

            val economyService = getEconomyServiceOrFail()
            val transactionResult = economyService.getOrCreateAccount(src.uniqueId).get()
                    .withdraw(economyService.defaultCurrency, BigDecimal(takeAmount), transactionCause)
            if (transactionResult.result != ResultType.SUCCESS) {
                throw CommandException("You do not have enough money to do that.".red())
            }

            configManager.save(addpotConfig)
            src.sendMessage(addPotText)
            src.sendMessage(addPotValue)
        } else {
            val amount = args.getOne<Int>("addpotAmount").orElse(0)
            val DisplayAmount = Integer.toString(amount)
            val addPotValueConsole = Text.builder(DisplayAmount).color(TextColors.GREEN).build()
            val addPotTextConsole = Text.builder("The following amount has been added to the pot: ").build()
            val addpotConfigConsole = if (amount >= 0) {
                config.copy(internalData = config.internalData.copy(
                        pot = config.internalData.pot + amount))
            } else {
                val errorTextBuilder = Text.builder("Error: Could not add to the pot").color(TextColors.DARK_RED).build()
                throw CommandException(errorTextBuilder)
            }

            configManager.save(addpotConfigConsole)
            src.sendMessage(addPotTextConsole)
            src.sendMessage(addPotValueConsole)
        }
        return CommandResult.success()
    }

}
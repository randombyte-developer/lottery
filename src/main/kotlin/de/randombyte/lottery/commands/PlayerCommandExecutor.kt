package de.randombyte.lottery.commands

import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.command.spec.CommandExecutor
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.format.TextColors

abstract class PlayerCommandExecutor : CommandExecutor {
    companion object {
        val ERROR_TEXT = Text.of(TextColors.RED, "Must be executed by a player!")
    }

    override fun execute(src: CommandSource, args: CommandContext): CommandResult = if (src !is Player) {
        src.sendMessage(ERROR_TEXT)
        CommandResult.success()
    } else executedByPlayer(src, args)

    abstract fun executedByPlayer(player: Player, args: CommandContext): CommandResult
}
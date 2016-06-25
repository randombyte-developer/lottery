package de.randombyte.lottery.commands

import de.randombyte.lottery.ConfigManager
import de.randombyte.lottery.Lottery
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.command.spec.CommandExecutor

class DrawCommand : CommandExecutor {
    override fun execute(src: CommandSource, args: CommandContext): CommandResult {
        Lottery.draw(ConfigManager.loadConfig())
        return CommandResult.success()
    }
}
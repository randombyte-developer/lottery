package de.randombyte.lottery

import com.google.inject.Inject
import de.randombyte.lottery.commands.BuyTicketCommand
import de.randombyte.lottery.commands.DrawCommand
import de.randombyte.lottery.commands.InfoCommand
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.loader.ConfigurationLoader
import org.slf4j.Logger
import org.spongepowered.api.Sponge
import org.spongepowered.api.command.args.GenericArguments
import org.spongepowered.api.command.spec.CommandSpec
import org.spongepowered.api.config.DefaultConfig
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.event.cause.NamedCause
import org.spongepowered.api.event.game.GameReloadEvent
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.service.economy.EconomyService
import org.spongepowered.api.service.user.UserStorageService
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.format.TextColors
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.TimeUnit

@Plugin(id = Lottery.ID, name = Lottery.NAME, version = Lottery.VERSION, authors = arrayOf(Lottery.AUTHOR))
class Lottery @Inject constructor(val logger: Logger, @DefaultConfig(sharedRoot = true) val configLoader: ConfigurationLoader<CommentedConfigurationNode>) {
    companion object {
        const val ID = "lottery"
        const val NAME = "Lottery"
        const val VERSION = "v0.1.2"
        const val AUTHOR = "RandomByte"

        val PLUGIN_CAUSE: Cause = Cause.of(NamedCause.source(this))

        fun draw(config: Config) {
            val ticketBuyers = config.internalData.boughtTickets.map { Collections.nCopies(it.value, it.key) }.flatten()
            if (ticketBuyers.isEmpty()) {
                broadcast(Text.of(TextColors.GRAY, "The lottery pot is empty, the draw is postponed!"))
                return
            }
            Collections.shuffle(ticketBuyers) //Here comes the randomness
            playerWon(config, ticketBuyers.first())
            resetPot(config)
        }

        private fun playerWon(config: Config, uuid: UUID) {
            val playerName = Sponge.getServiceManager().provide(UserStorageService::class.java).get().get(uuid).get().name
            val pot = getPot(config)
            broadcast(config.drawMessage.apply(mapOf("player" to playerName, "pot" to pot)).build())
            val economyService = getEconomyServiceOrFail()
            economyService.getOrCreateAccount(uuid).get().deposit(economyService.defaultCurrency, BigDecimal(pot), PLUGIN_CAUSE)
        }

        private fun broadcast(text: Text) = Sponge.getServer().broadcastChannel.send(text)
        private fun resetPot(config: Config) =
                ConfigManager.saveConfig(config.copy(internalData = config.internalData.copy(pot = 0, boughtTickets = emptyMap())))
        fun getPot(config: Config) = config.internalData.pot * (config.payoutPercentage / 100.0)

        fun getEconomyServiceOrFail(): EconomyService = Sponge.getServiceManager().provide(EconomyService::class.java)
                .orElseThrow { RuntimeException("No economy plugin loaded!") }
    }

    @Listener
    fun onInit(event: GameInitializationEvent) {
        ConfigManager.configLoader = configLoader

        Sponge.getCommandManager().register(this, CommandSpec.builder()
                .child(CommandSpec.builder()
                        .permission("lottery.ticket.buy")
                        .executor(BuyTicketCommand())
                        .arguments(GenericArguments.optional(GenericArguments.integer(Text.of("ticketAmount"))))
                        .build(), "buy")
                .child(CommandSpec.builder()
                        .permission("lottery.draw")
                        .executor(DrawCommand())
                        .build(), "draw")
                .child(CommandSpec.builder()
                        .executor(InfoCommand())
                        .build(), "info")
                .build(), "lottery")

        resetTask(ConfigManager.loadConfig())

        logger.info("$NAME loaded: $VERSION")
    }

    @Listener
    fun onReload(event: GameReloadEvent) {
        val config = ConfigManager.loadConfig()
        resetTask(config)
        logger.info("Reloaded! Next draw in ${config.drawInterval.toString()}!")
    }

    private fun resetTask(config: Config) {
        Sponge.getScheduler().getScheduledTasks(this).forEach { it.cancel() }
        Sponge.getScheduler().createTaskBuilder()
                .async()
                .interval(config.drawInterval.seconds, TimeUnit.SECONDS)
                .delay(config.drawInterval.seconds, TimeUnit.SECONDS)
                .execute { -> draw(ConfigManager.loadConfig()) }
                .submit(this)
    }
}
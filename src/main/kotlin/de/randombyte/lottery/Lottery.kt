package de.randombyte.lottery

import com.google.inject.Inject
import de.randombyte.kosp.PlayerExecutedCommand
import de.randombyte.kosp.bstats.BStats
import de.randombyte.kosp.config.ConfigManager
import de.randombyte.kosp.extensions.getUser
import de.randombyte.kosp.extensions.gray
import de.randombyte.kosp.extensions.toText
import de.randombyte.kosp.getServiceOrFail
import de.randombyte.lottery.commands.BuyTicketCommand
import de.randombyte.lottery.commands.InfoCommand
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.loader.ConfigurationLoader
import org.slf4j.Logger
import org.spongepowered.api.Sponge
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.command.args.GenericArguments
import org.spongepowered.api.command.spec.CommandSpec
import org.spongepowered.api.config.DefaultConfig
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.event.cause.NamedCause
import org.spongepowered.api.event.game.GameReloadEvent
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.scheduler.Task
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.service.economy.EconomyService
import org.spongepowered.api.text.Text
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

@Plugin(id = Lottery.ID, name = Lottery.NAME, version = Lottery.VERSION, authors = arrayOf(Lottery.AUTHOR))
class Lottery @Inject constructor(
        val logger: Logger,
        @DefaultConfig(sharedRoot = true) configLoader: ConfigurationLoader<CommentedConfigurationNode>,
        val metrics: BStats,
        pluginContainer: PluginContainer) {

    companion object {
        const val ID = "lottery"
        const val NAME = "Lottery"
        const val VERSION = "1.3.1"
        const val AUTHOR = "RandomByte"
    }

    val configManager = ConfigManager(
            configLoader = configLoader,
            clazz = Config::class.java,
            simpleTextSerialization = true,
            simpleTextTemplateSerialization = true,
            simpleDurationSerialization = true)

    val PLUGIN_CAUSE: Cause = Cause.of(NamedCause.source(pluginContainer))
    // Set on startup in setDurationUntilDraw()
    lateinit var nextDraw: Instant

    @Listener
    fun onInit(event: GameInitializationEvent) {
        Sponge.getCommandManager().register(this, CommandSpec.builder()
                .child(CommandSpec.builder()
                        .permission("lottery.ticket.buy")
                        .executor(BuyTicketCommand(configManager, PLUGIN_CAUSE))
                        .arguments(GenericArguments.optional(GenericArguments.integer("ticketAmount".toText())))
                        .build(), "buy")
                .child(CommandSpec.builder()
                        .permission("lottery.draw")
                        .executor(object : PlayerExecutedCommand() {
                            override fun executedByPlayer(player: Player, args: CommandContext): CommandResult {
                                draw(configManager.get())
                                return CommandResult.success()
                            }
                        })
                        .build(), "draw")
                .child(CommandSpec.builder()
                        .executor(InfoCommand(configManager, durationUntilDraw = { getDurationUntilDraw() }))
                        .build(), "info")
                .build(), "lottery", "lot")

        val config = configManager.get()
        // Manually set the duration because the draw task in resetTasks() may be executed too late
        setDurationUntilDraw(config)
        resetTasks(config)

        logger.info("$NAME loaded: $VERSION")
    }

    @Listener
    fun onReload(event: GameReloadEvent) {
        resetTasks(configManager.get())
        logger.info("Reloaded!")
    }

    fun draw(config: Config) {
        val ticketBuyers = config.internalData.boughtTickets.map { Collections.nCopies(it.value, it.key) }.flatten()
        if (ticketBuyers.isEmpty()) {
            broadcast("The lottery pot is empty, the draw is postponed!".gray())
            return
        }

        Collections.shuffle(ticketBuyers) // Here comes the randomness
        val winner = ticketBuyers.first()

        playerWon(config, winner)

        val drawEvent = DrawEvent(winner, config.calculatePot(), PLUGIN_CAUSE)
        Sponge.getEventManager().post(drawEvent)

        resetPot(config)
    }

    fun playerWon(config: Config, uuid: UUID) {
        val playerName = uuid.getUser()?.name ?: "unknown"
        val pot = config.calculatePot()

        val defaultCurrency = getDefaultCurrency()
        val message = config.messages.drawMessageBroadcast.apply(mapOf(
                "winnerName" to playerName,
                "pot" to pot,
                "currencySymbol" to defaultCurrency.symbol,
                "currencyName" to defaultCurrency.pluralDisplayName
        )).build()
        broadcast(message)

        val economyService = getEconomyServiceOrFail()
        economyService.getOrCreateAccount(uuid).get().deposit(economyService.defaultCurrency, BigDecimal(pot), PLUGIN_CAUSE)
    }

    fun resetPot(config: Config) {
        val newInternalData = config.internalData.copy(pot = 0, boughtTickets = emptyMap())
        val newConfig = config.copy(internalData = newInternalData)
        configManager.save(newConfig)
    }

    fun getDurationUntilDraw(): Duration = Duration.between(Instant.now(), nextDraw)

    fun setDurationUntilDraw(config : Config) {
        nextDraw = Instant.ofEpochSecond(Instant.now().epochSecond + config.drawInterval.seconds)
    }

    fun resetTasks(config: Config) {
        Sponge.getScheduler().getScheduledTasks(this).forEach { it.cancel() }

        // Don't make the tasks async because it may happen that the broadcast and draw task are
        // executed simultaneously. Irritating messages could be produced.

        Task.builder()
                .interval(config.drawInterval.seconds, TimeUnit.SECONDS)
                .execute { ->
                    val currentConfig = configManager.get()
                    draw(currentConfig)
                    setDurationUntilDraw(currentConfig)
                }.submit(this)

        Task.builder()
                .delay(5, TimeUnit.SECONDS) // First start: let economy plugin load
                .interval(config.broadcasts.timedBroadcastInterval.seconds, TimeUnit.SECONDS)
                .execute { ->
                    val currentConfig = configManager.get()
                    val currency = getDefaultCurrency()
                    val broadcastText = currentConfig.messages.broadcast.apply(mapOf(
                            "currencySymbol" to currency.symbol,
                            "currencyName" to currency.name,
                            "pot" to currentConfig.calculatePot()
                    )).build()
                    broadcast(broadcastText)
                }.submit(this)
    }
}

fun getEconomyServiceOrFail() = getServiceOrFail(EconomyService::class, "No economy plugin loaded!")
fun getDefaultCurrency(): Currency = getEconomyServiceOrFail().defaultCurrency

fun broadcast(text: Text) = Sponge.getServer().broadcastChannel.send(text)

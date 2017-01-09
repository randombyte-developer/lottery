package de.randombyte.lottery

import com.google.inject.Inject
import de.randombyte.kosp.PlayerExecutedCommand
import de.randombyte.kosp.ServiceUtils
import de.randombyte.kosp.bstats.BStats
import de.randombyte.kosp.config.ConfigManager
import de.randombyte.kosp.extensions.getUser
import de.randombyte.kosp.extensions.gray
import de.randombyte.kosp.extensions.toText
import de.randombyte.kosp.extensions.typeToken
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
        pluginContainer : PluginContainer) {

    companion object {
        const val ID = "lottery"
        const val NAME = "Lottery"
        const val VERSION = "v1.2"
        const val AUTHOR = "RandomByte"
    }

    val configManager = ConfigManager(
            configLoader = configLoader,
            clazz = Config::class,
            formattingTextSerialization = true,
            simpleTextTemplateSerialization = true,
            additionalSerializers = {
                registerType(Duration::class.typeToken, NewDurationSerializer)
            })

    val PLUGIN_CAUSE = Cause.of(NamedCause.source(pluginContainer))
    // Set on startup in resetTasks()
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
                .build(), "lottery")

        resetTasks(configManager.get())

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
        playerWon(config, ticketBuyers.first())
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

    fun getDurationUntilDraw() = Duration.between(Instant.now(), nextDraw)

    fun resetTasks(config: Config) {
        Sponge.getScheduler().getScheduledTasks(this).forEach { it.cancel() }

        Task.builder()
                .async()
                .interval(config.drawInterval.seconds, TimeUnit.SECONDS)
                .execute { ->
                    val currentConfig = configManager.get()
                    draw(currentConfig)
                    nextDraw = Instant.ofEpochSecond(Instant.now().epochSecond + currentConfig.drawInterval.seconds)
                }.submit(this)

        Task.builder()
                .async()
                .delay(5, TimeUnit.SECONDS) // First start: let economy plugin load
                .interval(config.broadcasts.timedBroadcastInterval.seconds, TimeUnit.SECONDS)
                .execute { ->
                    val currentConfig = configManager.get()
                    val currency = getDefaultCurrency()
                    val broadcastText = currentConfig.messages.broadcast.apply(mapOf(
                            "currencySymbol" to currency.symbol,
                            "currencyName" to currency.name,
                            "pot" to config.calculatePot()
                    )).build()
                    broadcast(broadcastText)
                }.submit(this)
    }
}

fun getEconomyServiceOrFail() = ServiceUtils.getServiceOrFail(EconomyService::class, "No economy plugin loaded!")
fun getDefaultCurrency() = getEconomyServiceOrFail().defaultCurrency

fun broadcast(text: Text) = Sponge.getServer().broadcastChannel.send(text)
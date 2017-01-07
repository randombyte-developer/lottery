package de.randombyte.lottery

import com.google.inject.Inject
import de.randombyte.kosp.ServiceUtils
import de.randombyte.kosp.bstats.BStatsMetrics
import de.randombyte.kosp.config.ConfigManager
import de.randombyte.kosp.extensions.gray
import de.randombyte.kosp.extensions.typeToken
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
import org.spongepowered.api.scheduler.Task
import org.spongepowered.api.service.economy.EconomyService
import org.spongepowered.api.service.user.UserStorageService
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.action.TextActions
import org.spongepowered.api.text.format.TextColors
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

@Plugin(id = Lottery.ID, name = Lottery.NAME, version = Lottery.VERSION, authors = arrayOf(Lottery.AUTHOR))
class Lottery @Inject constructor(
        val logger: Logger,
        @DefaultConfig(sharedRoot = true) configLoader: ConfigurationLoader<CommentedConfigurationNode>,
        val metrics: BStatsMetrics) {

    companion object {
        const val ID = "lottery"
        const val NAME = "Lottery"
        const val VERSION = "v1.2"
        const val AUTHOR = "RandomByte"
    }

    val configManager = ConfigManager(
            configLoader = configLoader,
            clazz = Config::class,
            hyphenSeparatedKeys = true,
            formattingTextSerialization = true,
            simpleTextTemplateSerialization = true,
            additionalSerializers = {
                registerType(Duration::class.typeToken, DurationSerializer)
            })

    val PLUGIN_CAUSE = Cause.of(NamedCause.source(this))
    // Set on startup in resetTasks()
    lateinit var nextDraw: Instant

    @Listener
    fun onInit(event: GameInitializationEvent) {
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

        resetTasks(configManager.get())

        logger.info("$NAME loaded: $VERSION")
    }

    @Listener
    fun onReload(event: GameReloadEvent) {
        resetTasks(configManager.get())
        logger.info("Reloaded! Next draw in ${getDurationUntilDraw().toMinutes()} minutes!")
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
        val playerName = ServiceUtils
                .getServiceOrFail(UserStorageService::class.java, "UserStorageService could not be loaded!")
                .get(uuid)?.orElse(null)?.name ?: "unknown"
        val pot = calculatePot(config)

        val defaultCurrency = getDefault()
        val parameters = mapOf("winnerName" to playerName, "pot" to pot, "currencySymbol" to defaultCurrency.symbol,
                "currencyName" to defaultCurrency.pluralDisplayName)
        val message = config.drawMessage.apply(parameters).build()
        broadcast(message)

        val economyService = getEconomyServiceOrFail()
        economyService.getOrCreateAccount(uuid).get().deposit(economyService.defaultCurrency, BigDecimal(pot), PLUGIN_CAUSE)
    }

    fun broadcast(text: Text) = Sponge.getServer().broadcastChannel.send(text)
    fun resetPot(config: Config) {
        val newInternalData = config.internalData.copy(pot = 0, boughtTickets = emptyMap())
        val newConfig = config.copy(internalData = newInternalData)
        configManager.save(newConfig)
    }

    fun calculatePot(config: Config) = config.internalData.pot * (config.payoutPercentage / 100.0)
    fun getDurationUntilDraw() = Duration.between(Instant.now(), nextDraw)

    fun getEconomyServiceOrFail() = ServiceUtils.getServiceOrFail(EconomyService::class.java, "No economy plugin loaded!")
    fun getDefault() = getEconomyServiceOrFail().defaultCurrency

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
                .interval(config.broadcasts.timedBroadcastInterval.seconds, TimeUnit.SECONDS)
                .execute { ->
                    broadcast(Text.builder()
                            .append(Text.of(TextColors.GOLD, "Current pot is at "))
                            .append(Text.of(TextColors.AQUA, calculatePot(configManager.get())))
                            .append(getEconomyServiceOrFail().defaultCurrency.symbol)
                            .append(Text.of(TextColors.GOLD, "! Use "))
                            .append(Text.builder("/lottery buy [amount] ").color(TextColors.AQUA).
                                    onClick(TextActions.suggestCommand("/lottery buy")).build())
                            .append(Text.of(TextColors.GOLD, "to buy tickets!"))
                            .build())
                }.submit(this)
    }
}
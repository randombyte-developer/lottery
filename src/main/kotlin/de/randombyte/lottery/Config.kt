package de.randombyte.lottery

import com.google.common.reflect.TypeToken
import ninja.leaping.configurate.ConfigurationOptions
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.loader.ConfigurationLoader
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializers
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.TextTemplate
import org.spongepowered.api.text.format.TextColors
import java.time.Duration
import java.util.*

object ConfigManager {
    lateinit var configLoader: ConfigurationLoader<CommentedConfigurationNode>
    private val options = ConfigurationOptions.defaults().setSerializers(TypeSerializers
            .getDefaultSerializers()
            .newChild()
            .registerType(TypeToken.of(Duration::class.java), DurationSerializer))

    private fun getRootNode() = configLoader.load(options)
    fun loadConfig(): Config {
        val config = getRootNode().getValue(TypeToken.of(Config::class.java))
        return if (config != null) config else {
            saveConfig(Config()) //Set default Config when no config was found
            loadConfig()
        }
    }
    fun saveConfig(config: Config) = configLoader.save(getRootNode().setValue(TypeToken.of(Config::class.java), config))
}

@ConfigSerializable
data class Config(
        @Setting(comment = "Format explanation: http://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-")
            val drawInterval: Duration = Duration.ofHours(3),
        @Setting val ticketCosts: Int = 100,
        @Setting(comment = "How much of the pot should be given to the winner") val payoutPercentage: Int = 90,
        @Setting val drawMessage: TextTemplate = TextTemplate.of(
                TextTemplate.arg("player").color(TextColors.GOLD),
                Text.of(TextColors.GRAY, " won the lottery pot of "),
                TextTemplate.arg("pot").color(TextColors.AQUA),
                Text.of(TextColors.GRAY, "$!")
        ),
        @Setting(comment = "The max. amount of tickets a player can buy") val maxTickets: Int = 5,
        @Setting(comment = "Don't modify this!") val internalData: InternalData = InternalData()
)

@ConfigSerializable
data class InternalData(
        @Setting val boughtTickets: Map<UUID, Int> = emptyMap(),
        @Setting val pot: Int = 0
)
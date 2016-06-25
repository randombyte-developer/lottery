package de.randombyte.lottery

import com.google.common.reflect.TypeToken
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer
import java.time.Duration

object DurationSerializer : TypeSerializer<Duration> {
    override fun serialize(type: TypeToken<*>, duration: Duration, value: ConfigurationNode) {
        value.value = duration.toString()
    }

    override fun deserialize(type: TypeToken<*>, value: ConfigurationNode) = Duration.parse(value.string)
}
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import mu.KotlinLogging
import org.redisson.Redisson
import org.redisson.config.Config

const val url = "https://ateliers-auguste.fr/en/products/mini-marly-gold-edition-cuir-box-taupe"

val logger = KotlinLogging.logger {}

suspend fun isBagInStock(): Boolean {
    val client = HttpClient(CIO)
    val response = client.get(url)
    val body = response.body<String>()
    val searchResult = "<span data-add-to-cart-text>\\n\\s*Sold Out".toRegex(RegexOption.MULTILINE).find(body)
    return searchResult == null
}

suspend fun main(args: Array<String>) = coroutineScope {
    val config = Config()
    val singleServerConfig = config.useSingleServer()
    singleServerConfig.address = "redis://${args[1]}:6379"
    singleServerConfig.connectionMinimumIdleSize = 0
    singleServerConfig.connectionPoolSize = 1
    val reactiveClient = Redisson.create(config).reactive()
    logger.info { "Initialization: getting client list to check system is working" }
    val reactiveCLients = reactiveClient.getSet<String>("clients")
    val clientString = buildString {
        append("[")
        reactiveCLients.iterator().asFlow().collect {
            append("$it,")
        }
        append("]")

    }
    logger.info { "Client list: $clientString" }

    val bot = bot {
        token = args[0]
        dispatch {
            command("start") {
                val clientId = message.chat.id.toString()
                logger.info { "Subscribing new client: $clientId" }
                reactiveCLients.add(clientId).awaitSingle()
                logger.info { "New client subscribed: $clientId" }
            }
        }
    }

    logger.info { "Scheduling periodic task" }

    launch {
        while (true) {
            delay(60 * 60 * 1000)
            logger.info { "Task occured" }
            logger.info { "Checking whether bag in stock" }
            if (isBagInStock()) {
                logger.info { "Bug in stock" }
                reactiveCLients.iterator().asFlow().collect { client ->
                    logger.info { "Sending to $client" }
                    bot.sendMessage(ChatId.fromId(client.toLong()), "Bag is available! You need to buy it urgently $url")
                }
            }
        }
    }
    bot.startPolling()
}

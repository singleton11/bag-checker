import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.newClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.util.*
import kotlin.concurrent.schedule

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
    logger.info { "This is zhopa" }
    newClient(Endpoint.from("${args[1]}:6379")).use { redisClient ->
        logger.info { "Initialization: getting client list to check system is working" }
        val clients = redisClient.lrange("clients", 0, -1)
        val clientsString = buildString {
            append("[")
            for (client in clients) {
                append("$client,")
            }
            append("]")
        }

        logger.info { "Client list: $clientsString" }

        val bot = bot {
            token = args[0]
            dispatch {
                command("start") {
                    val clientId = message.chat.id.toString()
                    logger.info { "New client subscribed $clientId" }
                    redisClient.lpush("clients", clientId)
                }
            }
        }

        launch {
            logger.info { "Scheduling periodic task" }
            Timer().schedule(0, 60 * 60 * 1000) {
                logger.info { "Task occured" }
                runBlocking {
                    logger.info { "Checking whether bag in stock" }
                    if (isBagInStock()) {
                        logger.info { "Bug in stock" }
                        val clients = redisClient.lrange("clients", 0, -1)
                        logger.info { "Clients to send a message $clients" }
                        for (client in clients) {
                            logger.info { "Sending to $client" }
                            bot.sendMessage(ChatId.fromId(client.toLong()), "Сумка доступна! Бегом покупать $url")
                        }
                    }
                }
            }
        }
        bot.startPolling()
    }
}

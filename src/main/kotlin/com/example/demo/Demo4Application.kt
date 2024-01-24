package com.example.demo

import io.r2dbc.spi.Option
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.r2dbc.R2dbcConnectionDetails
import org.springframework.boot.runApplication
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.MySqlDialect
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.testcontainers.containers.MySQLContainer
import java.math.BigInteger
import java.time.Instant
import kotlin.random.Random

@SpringBootApplication
class Demo4Application

fun main(args: Array<String>) {
    runApplication<Demo4Application>(*args)
}

@Configuration
class ApplicationConfiguration {
    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer<*> {
        val container = MySQLContainer("mysql:8")
        container.withUsername("root")
        return container
    }

    @Bean(initMethod = "migrate")
    fun flyway(connectionDetails: R2dbcConnectionDetails): Flyway {
        val options = connectionDetails.connectionFactoryOptions
        val driver = options.getRequiredValue(Option.valueOf<String>("driver")) as String
        val host = options.getRequiredValue(Option.valueOf<String>("host")) as String
        val port = options.getRequiredValue(Option.valueOf<Int>("port")) as Int
        val database = options.getRequiredValue(Option.valueOf<String>("database")) as String
        val user = options.getRequiredValue(Option.valueOf<String>("user")) as String
        val password = options.getRequiredValue(Option.valueOf<String>("password")) as String
        return Flyway(
            Flyway.configure()
                .dataSource(
                    "jdbc:${driver}://${host}:${port}/${database}",
                    user,
                    password
                )
        )
    }

    @Bean
    fun dbConverter(
        serializer: PublicKeySerializerDBConverter,
        deserializer: PublicKeyDeserializerDBConverter
    ): R2dbcCustomConversions {
        return R2dbcCustomConversions.of(MySqlDialect.INSTANCE, arrayListOf(serializer, deserializer));
    }
}

data class Account(
    @Id
    val publicKey: PublicKey,
    val balance: Long,
    val representative: PublicKey,
    val persistedAt: Instant? = null,
) : Persistable<PublicKey> {
    override fun getId(): PublicKey {
        return publicKey
    }

    override fun isNew(): Boolean {
        return persistedAt == null
    }
}


@Component
class PublicKeySerializerDBConverter : Converter<PublicKey, ByteArray> {
    override fun convert(source: PublicKey): ByteArray {
        return source.value;
    }
}

data class PublicKey(val value: ByteArray) {
    init {
        require(value.size == 32){"Given publicKey has just ${value.size} bytes instead of 32"}
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicKey) return false

        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}

@Component
class PublicKeyDeserializerDBConverter : Converter<ByteArray, PublicKey> {
    override fun convert(source: ByteArray): PublicKey {
        return PublicKey(source)
    }
}

@Service
class MyService(val repository: AccountRepository, val flyway: Flyway) {
    @PostConstruct
    fun start() {
        val publicKey = PublicKey(Random.Default.nextBytes(32))
        val account = Account(publicKey, Long.MAX_VALUE, publicKey)
        runBlocking {
            repository.save(account)
            repository.findAllWeights()
        }

    }
}


interface AccountRepository : CoroutineCrudRepository<Account, PublicKey> {

    @Query("SELECT representative AS public_key, SUM(balance) AS weight FROM account GROUP BY representative")
    suspend fun findAllWeights(): List<AmountView>
}

data class AmountView(
    val publicKey: PublicKey,
    val weight: BigInteger
)
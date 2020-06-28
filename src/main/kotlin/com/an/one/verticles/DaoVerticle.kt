package com.an.one.verticles

import com.an.one.models.ModelCodec
import com.an.one.models.User
import com.an.one.utils.EventAddresses.Companion.CONFIG_GET
import com.an.one.utils.EventAddresses.Companion.DAO_GET_USER
import com.an.one.utils.EventAddresses.Companion.DAO_UPDATE_USER
import io.vertx.core.eventbus.Message
import io.vertx.kotlin.core.eventbus.requestAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.awaitBlocking
import org.hibernate.reactive.stage.Stage
import javax.persistence.Persistence

class DaoVerticle : CoroutineVerticle() {
  private lateinit var sessionFactory: Stage.SessionFactory
  override suspend fun start() {
    val eventBus = vertx.eventBus()
    eventBus.registerDefaultCodec(User::class.java, ModelCodec<User>())

    val sqlUrl = eventBus.requestAwait<String>(CONFIG_GET, "sql_url").body()
    val sqlUser = eventBus.requestAwait<String>(CONFIG_GET, "sql_user").body()
    val sqlPassword = eventBus.requestAwait<String>(CONFIG_GET, "sql_password").body()

    sessionFactory = awaitBlocking {
      Persistence.createEntityManagerFactory(
        "anone", mapOf(
          Pair("javax.persistence.jdbc.url", sqlUrl),
          Pair("javax.persistence.jdbc.user", sqlUser),
          Pair("javax.persistence.jdbc.password", sqlPassword)
        )
      ).unwrap(Stage.SessionFactory::class.java)
    } ?: throw NullPointerException()

    eventBus.consumer<Unit>(DAO_GET_USER, this::getUser)
    eventBus.consumer<User>(DAO_UPDATE_USER, this::updateUser)

    val user = eventBus.requestAwait<User>(DAO_GET_USER, null).body()
    if (user == null) {
      awaitBlocking {
        sessionFactory.withTransaction { session, _ ->
          session.persist(User())
        }.toCompletableFuture().join()
      }
    }
  }

  private fun getUser(message: Message<Unit>) {
    sessionFactory.withSession {
      it.find(User::class.java, 1L).thenAccept { user ->
        message.reply(user)
      }
    }
  }

  private fun updateUser(message: Message<User>) {
    sessionFactory.withTransaction { session, _ ->
      session.merge(message.body())
        .whenComplete { _, e ->
          if (e != null){
            message.reply(e)
          }else {
            message.reply(null)
          }
        }
    }
  }
}

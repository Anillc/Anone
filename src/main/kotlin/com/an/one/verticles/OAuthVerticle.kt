package com.an.one.verticles

import com.an.one.models.ModelCodec
import com.an.one.utils.AOAuth2AccessToken
import com.an.one.utils.EventAddresses.Companion.DAO_UPDATE_USER
import com.an.one.utils.EventAddresses.Companion.MS_FILE_REFRESH
import com.an.one.utils.EventAddresses.Companion.OAUTH_AUTH
import com.an.one.utils.EventAddresses.Companion.OAUTH_GET_AUTH_URL
import com.an.one.utils.EventAddresses.Companion.OAUTH_GET_TOKEN
import com.an.one.utils.EventAddresses.Companion.OAUTH_REFRESH
import com.an.one.utils.EventAddresses.Companion.OAUTH_STOP
import com.an.one.utils.Utils.Companion.user
import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.oauth.OAuth20Service
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.core.json.Json
import io.vertx.kotlin.core.eventbus.requestAwait
import io.vertx.kotlin.core.undeployAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.awaitBlocking
import kotlinx.coroutines.launch
import java.lang.StringBuilder

class OAuthVerticle : CoroutineVerticle() {
  private lateinit var eventBus: EventBus
  private lateinit var service: OAuth20Service
  private var token: OAuth2AccessToken? = null
  override suspend fun start() {
    eventBus = vertx.eventBus()
    eventBus.registerDefaultCodec(OAuth2AccessToken::class.java, ModelCodec<OAuth2AccessToken>())
    eventBus.registerDefaultCodec(AOAuth2AccessToken::class.java, ModelCodec<AOAuth2AccessToken>())

    eventBus.consumer<Unit>(OAUTH_STOP, this::stop)

    val user = user(eventBus) ?: throw Exception("unknown error")

    if (user.msAppKey == "" || user.msSecret == "") {
      return
    }

    service = ServiceBuilder(user.msAppKey)
      .apiSecret(user.msSecret)
      .defaultScope("files.readwrite offline_access")
      .callback("${user.appUrl}/user/auth")
      .build(MicrosoftAzureActiveDirectory20Api.instance())

    if (user.msToken != "") {
      token = Json.decodeValue(user.msToken, AOAuth2AccessToken::class.java) as OAuth2AccessToken?
    }

    eventBus.consumer<Unit>(OAUTH_GET_AUTH_URL, this::getAuthUrl)
    eventBus.consumer<String>(OAUTH_AUTH, this::auth)
    eventBus.consumer<Unit>(OAUTH_GET_TOKEN, this::getToken)
    eventBus.consumer<Unit>(OAUTH_REFRESH, this::refresh)

    if (token != null) {
      eventBus.publish(OAUTH_REFRESH, null)
      vertx.setPeriodic(user.tokenRefreshCycle) {
        eventBus.publish(OAUTH_REFRESH, null)
      }
      vertx.setPeriodic(user.fileRefreshCycle) {
        eventBus.publish(MS_FILE_REFRESH, null)
      }
    }
  }

  override suspend fun stop() {
    eventBus.unregisterDefaultCodec(OAuth2AccessToken::class.java)
    eventBus.unregisterDefaultCodec(AOAuth2AccessToken::class.java)
  }

  private fun getAuthUrl(message: Message<Unit>) {
    message.reply(service.authorizationUrl)
  }

  private fun auth(message: Message<String>) {//回复string为错误，null为成功
    launch {
      try {
        val code = message.body()
        val nToken = awaitBlocking {
          service.getAccessToken(code)
        }
        if (nToken == null) {
          message.reply("null")
          return@launch
        }
        updateToken(nToken)
        message.reply(null)
      } catch (e: Exception) {
        message.reply(e.message)
      }
    }
  }

  private fun getToken(message: Message<Unit>) {
    message.reply(token)
  }

  private fun refresh(message: Message<Unit>) {
    launch {
      val refreshToken = token?.refreshToken ?: return@launch
      val nToken = awaitBlocking {
        service.refreshAccessToken(refreshToken)
      } ?: return@launch
      updateToken(nToken)
    }
  }

  private fun stop(message: Message<Unit>) {
    launch {
      vertx.undeployAwait(deploymentID)
      message.reply(null)
    }
  }

  private suspend fun updateToken(token: OAuth2AccessToken) {
    val user = user(eventBus) ?: return
    this.token = token
    user.msToken = Json.encode(token)
    val e = eventBus.requestAwait<Throwable?>(DAO_UPDATE_USER, user).body()
    e?.printStackTrace()
  }
}

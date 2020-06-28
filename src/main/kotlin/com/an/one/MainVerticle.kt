package com.an.one

import com.an.one.utils.EventAddresses
import com.an.one.utils.EventAddresses.Companion.INIT_START
import com.an.one.utils.EventAddresses.Companion.MAIN_DEPLOY
import com.an.one.utils.EventAddresses.Companion.MAIN_REDEPLOY_OAUTH
import com.an.one.utils.EventAddresses.Companion.MS_FILE_REFRESH
import com.an.one.verticles.*
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.core.eventbus.requestAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.launch

class MainVerticle : CoroutineVerticle() {
  private lateinit var eventBus: EventBus
  override suspend fun start() {
    vertx.deployVerticleAwait(InitVerticle())

    eventBus = vertx.eventBus()
    eventBus.consumer<Unit>(MAIN_DEPLOY, this::deployVerticles)
    eventBus.consumer<Unit>(MAIN_REDEPLOY_OAUTH, this::redeployOAuth)
    eventBus.publish(INIT_START, null)
  }

  private fun redeployOAuth(message: Message<Unit>) {//重新部署OAuthVerticle并刷新文件
    launch {
      eventBus.requestAwait<Unit>(EventAddresses.OAUTH_STOP, null)
      vertx.deployVerticleAwait(OAuthVerticle())
      eventBus.publish(MS_FILE_REFRESH, null)
    }
  }

  private fun deployVerticles(message: Message<Unit>) {
    launch {
      vertx.deployVerticleAwait(ConfigVerticle())
      vertx.deployVerticleAwait(DaoVerticle())
      vertx.deployVerticleAwait(OAuthVerticle())
      vertx.deployVerticleAwait(ApiVerticle())
    }
  }
}

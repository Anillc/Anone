package com.an.one.verticles.apis

import com.an.one.utils.EventAddresses
import com.an.one.utils.EventAddresses.Companion.MAIN_REDEPLOY_OAUTH
import com.an.one.utils.EventAddresses.Companion.OAUTH_AUTH
import com.an.one.utils.EventAddresses.Companion.OAUTH_GET_AUTH_URL
import com.an.one.utils.Utils
import com.an.one.utils.Utils.Companion.user
import com.an.one.utils.end
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.jwt.JWTOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.JWTAuthHandler
import io.vertx.kotlin.core.eventbus.requestAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.launch
import org.mindrot.jbcrypt.BCrypt

class UserVerticle(
  private val router: Router,
  private val jwtAuth: JWTAuth,
  private val jwtAuthHandler: JWTAuthHandler
) :
  CoroutineVerticle() {
  private lateinit var eventBus: EventBus
  override suspend fun start() {
    eventBus = vertx.eventBus()

    val user = Router.router(vertx)
    router.mountSubRouter("/user", user)

    user.get("/login").handler(this::userLogin)
    user.route().handler(jwtAuthHandler)
    user.get("/").handler(this::getUser)
    user.put("/").handler(this::updateUser)
    user.get("/auth_url").handler(this::msAuthUrl)
    user.get("/auth").handler(this::auth)
    user.get("/refresh").handler(this::refresh)
  }

  private fun userLogin(ctx: RoutingContext) {
    launch {
      val username = ctx.queryParam("username").joinToString(separator = " ")
      val password = ctx.queryParam("password").joinToString(separator = " ")
      val user = user(eventBus)
      if (user == null) {
        ctx.response().end(Utils.Reply(500, "user is null"))
        return@launch
      }
      if (user.name == username && BCrypt.checkpw(password, user.password)) {
        ctx.response().end(
          Utils.Reply(
            200, "登录成功", jwtAuth.generateToken(
              JsonObject(), JWTOptions()
                .setSubject(user.name)
                .setExpiresInMinutes(60 * 24 * 10)
            )
          )
        )
      } else {
        ctx.response().end(Utils.Reply(401, "用户名或密码错误"))
      }
    }
  }

  private fun getUser(ctx: RoutingContext) {
    launch {
      val user = user(eventBus)
      if (user == null) {
        ctx.response().end(Utils.Reply(500, "user is null"))
        return@launch
      }
      val data = JsonObject()
        .put("name", user.name)
        .put("appUrl", user.appUrl)
        .put("tokenRefreshCycle", user.tokenRefreshCycle)
        .put("fileRefreshCycle", user.fileRefreshCycle)
        .put("msNeedInit", user.msAppKey == "" || user.msSecret == "" || user.msToken == "")
      ctx.response().end(Utils.Reply(200, "successful", data))
    }
  }

  private fun updateUser(ctx: RoutingContext) {
    launch {
      val user = user(eventBus)
      if (user == null) {
        ctx.response().end(Utils.Reply(500, "user is null"))
        return@launch
      }
      val body = ctx.bodyAsJson
      if (body == null) {
        ctx.response().end(Utils.Reply(400, "Bad request"))
        return@launch
      }
      try {
        for ((key, value) in body) {
          when (key) {
            "name" -> user.name = value as String
            "password" -> user.password = Utils.hash(value as String)
            "appUrl" -> user.appUrl = value as String
            "msAppKey" -> user.msAppKey = value as String
            "msSecret" -> user.msSecret = value as String
            "tokenRefreshCycle" -> user.tokenRefreshCycle = (value as Int).toLong()
            "fileRefreshCycle" -> user.fileRefreshCycle = (value as Int).toLong()
            else -> {
              ctx.response().end(Utils.Reply(400, "Bad request"))
              return@launch
            }
          }
        }
        val e = eventBus.requestAwait<Throwable?>(EventAddresses.DAO_UPDATE_USER, user).body()
        if (e != null) {
          ctx.response().end(Utils.Reply(500, e.message))
        } else {
          ctx.response().end(Utils.Reply(200, "successful"))
          eventBus.publish(EventAddresses.MAIN_REDEPLOY_OAUTH, null) //刷新tokenRefreshCycle和fileRefreshCycle
        }
      } catch (e: Exception) {
        ctx.response().end(Utils.Reply(400, "Bad request"))
      }
    }
  }

  private fun msAuthUrl(ctx: RoutingContext) {
    launch {
      try {
        val url = eventBus.requestAwait<String>(OAUTH_GET_AUTH_URL, null).body()
        ctx.response().end(Utils.Reply(200, "successful", url))
      }catch (e:Exception){//OAuthVerticle未初始化时调用会抛出异常
        ctx.response().end(Utils.Reply(500, "请先初始化Microsoft账号"))
      }
    }
  }

  /*
  TODO
  这里是Microsoft的回调，但是当网页跳转回来的时候是没有jwt token的
  所以我们需要在这里跳转到前端，利用前端为请求添加验证头
   */
  private fun auth(ctx: RoutingContext) {
    launch {
      val list = ctx.queryParam("code")
      if (list.size == 0) {
        ctx.response().end("验证失败，请重试")
        return@launch
      }
      try {
        val res = eventBus.requestAwait<String?>(OAUTH_AUTH, list[0]).body()
        if (res != null) {
          ctx.response().end("验证失败，请重试")
          return@launch
        }
        ctx.response().end("验证成功w")
      }catch (e: Exception){//OAuthVerticle未初始化时调用会抛出异常
        ctx.response().end("请先初始化Microsoft账号")
      }
    }
  }

  private fun refresh(ctx: RoutingContext) {
    launch {
      eventBus.publish(MAIN_REDEPLOY_OAUTH, null)
      ctx.response().end(Utils.Reply(200, "successful"))
    }
  }
}

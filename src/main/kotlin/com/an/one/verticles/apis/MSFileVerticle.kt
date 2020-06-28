package com.an.one.verticles.apis

import com.an.one.models.ODFile
import com.an.one.utils.EventAddresses.Companion.MS_FILE_REFRESH
import com.an.one.utils.EventAddresses.Companion.OAUTH_GET_TOKEN
import com.an.one.utils.Utils
import com.an.one.utils.end
import com.github.scribejava.core.model.OAuth2AccessToken
import com.microsoft.graph.requests.extensions.GraphServiceClient
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.eventbus.requestAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.awaitBlocking
import kotlinx.coroutines.launch

class MSFileVerticle(private val router: Router) : CoroutineVerticle() {
  private lateinit var eventBus: EventBus
  private var root: ODFile? = null
  private var token: OAuth2AccessToken? = null
  private val client = GraphServiceClient.builder().authenticationProvider {
    val aToken = token ?: throw Exception("请先登录Microsoft账号")
    it.addHeader("Authorization", "bearer " + aToken.accessToken)
  }.buildClient()

  override suspend fun start() {
    eventBus = vertx.eventBus()

    val subRouter = Router.router(vertx)
    router.mountSubRouter("/files", subRouter)
    router.getWithRegex("/r/(?<file>.*)").handler(this::redirectFile)

    subRouter.getWithRegex("/(?<file>.*)").handler(this::file)

    eventBus.consumer<Unit>(MS_FILE_REFRESH, this::refreshFile)
    eventBus.publish(MS_FILE_REFRESH, null)
  }

  private fun file(ctx: RoutingContext) {
    val file = getFile(ctx.pathParam("file"))
    if (file == null) {
      ctx.response().end(Utils.Reply(404, "File not found"))
      return
    }
    ctx.response().end(Utils.Reply(200, "successful", getFileJsonObject(file)))
  }

  private fun redirectFile(ctx: RoutingContext) {
    val file = getFile(ctx.pathParam("file"))
    if (file == null) {
      ctx.response().end("未找到该文件w")
      return
    }
    if (file.type == ODFile.Type.FOLDER) {
      ctx.response().end("这是一个文件夹w")
      return
    }
    ctx.response().setStatusCode(302).putHeader("Location", file.url).end()
  }

  private fun refreshFile(message: Message<Unit>) {
    launch {
      token = getToken() ?: return@launch
      root = awaitBlocking {
        val page = client.me().drive().root().children().buildRequest().get()
        Utils.getODFile(client, page, "root", "/", null)
      }
    }
  }

  private fun getFile(iPath: String): ODFile? {
    var filePath: String = iPath.trim()
    if (filePath == "") {
      return root
    }
    if (filePath.endsWith("/")) {
      filePath = filePath.substring(0, filePath.length - 1)
    }
    val pathNames = filePath.split("/").toTypedArray()
    var cFile = root ?: return null
    for (path in pathNames) {
      var found = false
      for (icFile in cFile.files ?: return null) {
        if (path == icFile.name) {
          found = true
          cFile = icFile
          break
        }
      }
      if (!found) {
        return null
      }
    }
    return cFile
  }

  private fun getFileJsonObject(file: ODFile): JsonObject {
    val res = JsonObject()
    res.put("name", file.name)
    res.put("type", if (file.type == ODFile.Type.FILE) "file" else "folder")
    if (file.url != null) {
      res.put("url", file.url)
    }
    if (file.files != null) {
      val files = JsonArray()
      for (subFile in file.files) {
        val subFileJsonObject = JsonObject()
        subFileJsonObject.put("name", subFile.name)
        subFileJsonObject.put("type", if (subFile.type == ODFile.Type.FILE) "file" else "folder")
        files.add(subFileJsonObject)
      }
      res.put("files", files)
    }
    return res
  }

  private suspend fun getToken(): OAuth2AccessToken? {
    return try {
      eventBus.requestAwait<OAuth2AccessToken?>(OAUTH_GET_TOKEN, null).body()
    } catch (e: Exception) {//OAuthVerticle未初始化时调用会抛出异常
      null
    }
  }
}

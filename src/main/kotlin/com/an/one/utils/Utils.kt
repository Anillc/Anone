package com.an.one.utils

import com.an.one.models.ODFile
import com.an.one.models.User
import com.microsoft.graph.models.extensions.DriveItem
import com.microsoft.graph.models.extensions.IGraphServiceClient
import com.microsoft.graph.requests.extensions.IDriveItemCollectionPage
import io.vertx.core.eventbus.EventBus
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import io.vertx.kotlin.core.eventbus.requestAwait
import org.mindrot.jbcrypt.BCrypt

class Utils {
  data class Reply(val code: Int, val message: String?, val data: Any? = null)

  companion object {
    fun hash(password: String): String {
      return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    suspend fun user(eventBus: EventBus): User? {
      return eventBus.requestAwait<User>(EventAddresses.DAO_GET_USER, null).body()
    }

    fun getODFile(
      client: IGraphServiceClient,
      page: IDriveItemCollectionPage,
      folderName: String,
      path: String,
      driveItem: DriveItem?
    ): ODFile {
      var mPage = page
      val files = mutableListOf<ODFile>()
      val driveItems = mutableListOf<DriveItem>()
      while (true) {
        driveItems.addAll(mPage.currentPage)
        if (mPage.nextPage == null) {
          break
        }
        mPage = mPage.nextPage.buildRequest().get()
      }
      for (item in driveItems) {
        if (item.folder == null) {
          val jsonElement = item.rawObject["@microsoft.graph.downloadUrl"] ?: continue
          files.add(ODFile(ODFile.Type.FILE, item.name, path, jsonElement.asString, null, item))
        } else {
          try {
            val folderPage =
              client.me().drive().items().byId(item.id).children().buildRequest().get()
            files.add(getODFile(client, folderPage, item.name, path + item.name + "/", item))
          } catch (e: Exception) {
            e.printStackTrace()
          }
        }
      }
      return ODFile(ODFile.Type.FOLDER, folderName, "/", null, files.toTypedArray(), driveItem)
    }
  }
}

fun HttpServerResponse.end(reply: Utils.Reply) {
  this.putHeader("content-type", "application/json").end(Json.encode(reply))
}

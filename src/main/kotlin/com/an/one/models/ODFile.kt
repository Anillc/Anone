package com.an.one.models

import com.microsoft.graph.models.extensions.DriveItem

data class ODFile(
  val type: Type,
  val name: String,
  val path: String,
  val url: String?,
  val files: Array<ODFile>?,
  val driveItem: DriveItem?
) {
  enum class Type {
    FILE, FOLDER
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ODFile

    if (type != other.type) return false
    if (name != other.name) return false
    if (path != other.path) return false
    if (url != other.url) return false
    if (files != null) {
      if (other.files == null) return false
      if (!files.contentEquals(other.files)) return false
    } else if (other.files != null) return false
    if (driveItem != other.driveItem) return false

    return true
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + path.hashCode()
    result = 31 * result + (url?.hashCode() ?: 0)
    result = 31 * result + (files?.contentHashCode() ?: 0)
    result = 31 * result + (driveItem?.hashCode() ?: 0)
    return result
  }
}

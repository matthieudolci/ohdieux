package ca.ligature.ohdieux.ohdio

import play.api.libs.json._
import ca.ligature.ohdieux.persistence.ProgrammeType

private object Queries {
  import ApiClient._
  import RCModels._
  import RCModels.FetchResult._

  def buildGetPlaybackListByGlobalIdQuery(
      contentTypeId: Int,
      playbackListId: String
  ): Map[String, String] = {
    Map(
      "opname" -> "playbackListByGlobalId",
      "extensions" -> Json.stringify(
        JsObject(
          Seq(
            "persistedQuery" -> JsObject(
              Seq(
                "version" -> JsNumber(1),
                "sha256Hash" -> JsString(
                  "ae95ebffe69f06d85a0f287931b61e3b7bfb7485f28d4d906c376be5f830b8c0"
                )
              )
            )
          )
        )
      ),
      "variables" -> Json.stringify(
        JsObject(
          Seq(
            "params" -> JsObject(
              Seq(
                "contentTypeId" -> JsNumber(contentTypeId),
                "id" -> JsString(playbackListId)
              )
            )
          )
        )
      )
    )
  }

  def buildGetProgrammeByIdQuery(
      programmeType: ProgrammeType,
      programmeId: Int,
      pageNumber: Int
  ): Option[(Map[String, String], JsValue => JsValue)] =
    programmeType match {
      case ProgrammeType.Balado => {
        val extensions = JsObject(
          Seq(
            "persistedQuery" -> JsObject(
              Seq(
                "version" -> JsNumber(1),
                "sha256Hash" -> JsString(
                  "3b505d1f3b3935981802bccab9c9ee63bbf6c5c55a0b996287fb789e1a90e660"
                )
              )
            )
          )
        )

        val variables = JsObject(
          Seq(
            "params" -> JsObject(
              Seq(
                "context" -> JsString("web"),
                "forceWithoutCueSheet" -> JsBoolean(true),
                "id" -> JsNumber(programmeId),
                "pageNumber" -> JsNumber(pageNumber)
              )
            )
          )
        )

        return Some(
          Map(
            "opname" -> "programmeById",
            "extensions" -> Json.stringify(extensions),
            "variables" -> Json.stringify(variables)
          ),
          identity
        )
      }

      case ProgrammeType.EmissionPremiere => {
        val extensions = JsObject(
          Seq(
            "persistedQuery" -> JsObject(
              Seq(
                "version" -> JsNumber(1),
                "sha256Hash" -> JsString(
                  "7e961f5b1091589230b1f56ca6be609d26e5c475934235103afb55589a397e0c"
                )
              )
            )
          )
        )

        val variables = JsObject(
          Seq(
            "params" -> JsObject(
              Seq(
                "context" -> JsString("web"),
                "forceWithoutCueSheet" -> JsBoolean(false),
                "id" -> JsNumber(programmeId),
                "pageNumber" -> JsNumber(pageNumber)
              )
            )
          )
        )

        return Some(
          Map(
            "opname" -> "programmeById",
            "extensions" -> Json.stringify(extensions),
            "variables" -> Json.stringify(variables)
          ),
          identity
        )
      }

      case ProgrammeType.GrandeSerie => {
        val extensions = JsObject(
          Seq(
            "persistedQuery" -> JsObject(
              Seq(
                "version" -> JsNumber(1),
                "sha256Hash" -> JsString(
                  "c09912897e8cd67f8cacc65842a16d65b10829f06cdedfedc50bdeb0acb9989c"
                )
              )
            )
          )
        )

        val variables = JsObject(
          Seq(
            "params" -> JsObject(
              Seq(
                "context" -> JsString("web"),
                "forceWithoutCueSheet" -> JsBoolean(true),
                "id" -> JsNumber(programmeId),
                "pageNumber" -> JsNumber(pageNumber)
              )
            )
          )
        )

        return Some(
          Map(
            "opname" -> "programmeById",
            "extensions" -> Json.stringify(extensions),
            "variables" -> Json.stringify(variables)
          ),
          identity
        )
      }

      case ProgrammeType.Audiobook => {
        if (pageNumber > 1) {
          return None
        }
        val extensions = JsObject(
          Seq(
            "persistedQuery" -> JsObject(
              Seq(
                "version" -> JsNumber(1),
                "sha256Hash" -> JsString(
                  "a676e346c1993bbfcf6924eac2f3e3d45ad3055721c6949b74fbd0d33a505a59"
                )
              )
            )
          )
        )

        val variables = JsObject(
          Seq(
            "params" -> JsObject(
              Seq(
                "context" -> JsString("web"),
                "id" -> JsNumber(programmeId)
              )
            )
          )
        )

        return Some(
          Map(
            "opname" -> "audioBookById",
            "extensions" -> Json.stringify(extensions),
            "variables" -> Json.stringify(variables)
          ),
          audioBookTransform
        )
      }
    }
}

private def identity[T](x: T): T = x
private def audioBookTransform(x: JsValue): JsValue = {
  val patchedItems =
    (x \ "data" \ "audioBookById" \ "content" \ "contentDetail" \ "items")
      .getOrElse(Json.arr())
      .asInstanceOf[JsArray]
      .value
      .zipWithIndex
      .map(replaceAudiobookPlaylistItemId)

  // audiobooks reuse the same episode id. This hack ensures that at
  // least each segment has a relevant title. Otherwise, one random
  // segment tile will be picked as the episode title.
  Json.obj(
    "data" ->
      Json
        .obj(
          "programmeById" -> (x \ "data" \ "audioBookById")
            .getOrElse(Json.obj())
        )
        .deepMerge(
          Json.obj(
            "programmeById" -> Json.obj(
              "content" -> Json.obj(
                "contentDetail" -> Json.obj(
                  "items" -> patchedItems,
                  "pagedConfiguration" ->
                    Json.obj(
                      "pageMaxLength" -> 1,
                      "pageNumber" -> 1,
                      "pageSize" -> 25,
                      "totalNumberOfItems" -> patchedItems.length
                    )
                )
              )
            )
          )
        )
  )
}

private def replaceAudiobookPlaylistItemId(x: JsValue, index: Int): JsValue = {
  if (!x.isInstanceOf[JsObject]) {
    x
  } else {
    val playlistItemId =
      (x \ "playlistItemId" \ "globalId2" \ "id").getOrElse(JsString("unknown"))
    val inventedEpisodeId =
      -playlistItemId.as[JsString].value.toInt * 1000 - index
    x.asInstanceOf[JsObject]
      .deepMerge(
        Json.obj(
          "playlistItemId" -> Json
            .obj("globalId2" -> Json.obj("id" -> inventedEpisodeId.toString))
        )
      )
  }
}

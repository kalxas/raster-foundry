package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID
import spray.json._

import geotrellis.vector.Geometry
import geotrellis.slick.Projected

object ScenesJsonProtocol extends DefaultJsonProtocol {

  def jsArrayToList[T](jsArr: JsValue): List[T] = {
    jsArr match {
      case arr: JsArray => arr.elements.map(_.asInstanceOf[T]).to[List]
      case _ => List.empty[T]
    }
  }

  // Reimplements OptionFormat.read because OptionFormat is mysteriously not in scope
  def jsOptionToVal[T](jsOpt: JsValue): Option[T] = {
    jsOpt match {
      case JsNull => None
      case v: Any => Some(v.asInstanceOf[T])
    }
  }

  def formatTs(ts: String): Timestamp = {
    Timestamp.valueOf(
      ts.replace("Z", "").replace("T", " ")
    )
  }

  implicit object SceneWithRelatedFormat extends RootJsonFormat[Scene.WithRelated] {
    def write(scene: Scene.WithRelated) = JsObject(
      "id" -> scene.id.toJson,
      "createdAt" -> scene.createdAt.toJson,
      "createdBy" -> scene.createdBy.toJson,
      "modifiedAt" -> scene.modifiedAt.toJson,
      "modifiedBy" -> scene.modifiedBy.toJson,
      "organizationId" -> scene.organizationId.toJson,
      "ingestSizeBytes" -> scene.ingestSizeBytes.toJson,
      "visibility" -> scene.visibility.toJson,
      "tags" -> scene.tags.toJson,
      "datasource" -> scene.datasource.toJson,
      "sceneMetadata" -> scene.sceneMetadata.toJson,
      "cloudCover" -> scene.cloudCover.toJson,
      "acquisitionDate" -> scene.acquisitionDate.toJson,
      "thumbnailStatus" -> scene.thumbnailStatus.toJson,
      "boundaryStatus" -> scene.boundaryStatus.toJson,
      "sunAzimuth" -> scene.sunAzimuth.toJson,
      "sunElevation" -> scene.sunElevation.toJson,
      "name" -> scene.name.toJson,
      "tileFootprint" -> scene.tileFootprint.toJson,
      "dataFootprint" -> scene.dataFootprint.toJson,
      "metadataFiles" -> scene.metadataFiles.toJson,
      "images" -> scene.images.toJson,
      "thumbnails" -> scene.thumbnails.toJson,
      "ingestLocation" -> scene.ingestLocation.toJson
    )

    def read(value: JsValue): Scene.WithRelated = {
      val jsObject = value.asJsObject
      val fields = jsObject.getFields(
        "id", // 0
        "createdAt", // 1
        "createdBy", // 2
        "modifiedAt", // 3
        "modifiedBy", // 4
        "organizationId", // 5
        "ingestSizeBytes", // 6
        "visibility", // 7
        "tags", // 8
        "datasource", // 9
        "sceneMetadata", // 10
        "cloudCover", // 11
        "acquisitionDate", // 12
        "thumbnailStatus", // 13
        "boundaryStatus", // 14
        "sunAzimuth", // 15
        "sunElevation", // 16
        "name", // 17
        "tileFootprint", // 18
        "dataFootprint", // 19
        "metadataFiles", // 20
        "images", // 21
        "thumbnails", // 22
        "ingestLocation" //23
      )

      // we can't match { case(id, createdAt... ) => ??? } because that would require Tuple24
      Scene.WithRelated(
        UUID.fromString(StringJsonFormat.read(fields(0))), // id
        formatTs(StringJsonFormat.read(fields(1))), // createdAt
        StringJsonFormat.read(fields(2)), // createdBy
        formatTs(StringJsonFormat.read(fields(3))), // modifiedAt
        StringJsonFormat.read(fields(4)), // modifiedBy
        UUID.fromString(StringJsonFormat.read(fields(5))), // organizationId
        IntJsonFormat.read(fields(6)), // ingestSizeBytes
        Visibility.fromString(StringJsonFormat.read(fields(7))), // visibility
        jsArrayToList[String](fields(8)), // tags
        UUID.fromString(StringJsonFormat.read(fields(9))), // datasource
        fields(10).convertTo[Map[String, Any]], // sceneMetadata
        jsOptionToVal[Float](fields(11)), // cloudCover
        jsOptionToVal[Timestamp](fields(12)), //acquisitionDate
        JobStatus.fromString(StringJsonFormat.read(fields(13))), // thumbnailStatus
        JobStatus.fromString(StringJsonFormat.read(fields(14))), // boundaryStatus
        jsOptionToVal[Float](fields(15)), //sunAzimuth
        jsOptionToVal[Float](fields(16)), // sunElevation
        StringJsonFormat.read(fields(17)), // name
        jsOptionToVal[Projected[Geometry]](fields(18)), // tileFootprint
        jsOptionToVal[Projected[Geometry]](fields(19)), // dataFootprint
        jsArrayToList[String](fields(20)), // metadataFiles
        jsArrayToList[Image.WithRelated](fields(21)), // images
        jsArrayToList[Thumbnail](fields(22)), // thumbnails
        jsOptionToVal[String](fields(23)) // ingestLocation
      )
    }
  }
}

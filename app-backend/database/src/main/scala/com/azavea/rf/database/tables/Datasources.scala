package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields, UserFkFields, NameField}
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


/** Table that represents datasources
  *
  * Datasources can be user generated and help associate things like
  * tool compatibility and color correction settings to imagery sources
  */
class Datasources(_tableTag: Tag) extends Table[Datasource](_tableTag, "datasources")
    with NameField
    with TimestampFields
{
  def * = (id, createdAt, createdBy, modifiedAt, modifiedBy, organizationId, name,
           visibility, colorCorrection, extras) <> (Datasource.tupled, Datasource.unapply)

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val name: Rep[String] = column[String]("name")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val colorCorrection: Rep[Map[String, Any]] = column[Map[String, Any]]("color_correction", O.Length(2147483647,varying=false))
  val extras: Rep[Map[String, Any]] = column[Map[String, Any]]("extras", O.Length(2147483647,varying=false))

  lazy val organizationsFk = foreignKey("datasources_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("datasources_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("datasources_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object Datasources extends TableQuery(tag => new Datasources(tag)) with LazyLogging {
  type TableQuery = Query[Datasources, Datasource, Seq]

  implicit class withDatasourcesTableQuery[M, U, C[_]](datasources: Datasources.TableQuery) extends
    DatasourceTableQuery[M, U, C](datasources)

  /** List datasources given a page request
    *
    * @param pageRequest PageRequest information about sorting and page size
    */
  def listDatasources(pageRequest: PageRequest)(implicit database: DB): Future[PaginatedResponse[Datasource]] = {
    val datasourcePageAction = Datasources.page(pageRequest).result

    val datasourcePageQueryResult = database.db.run {
      datasourcePageAction
    }

    val datasourceTotalQueryResult = database.db.run {
      Datasources.length.result
    }

    for {
      totalDatasources <- datasourceTotalQueryResult
      datasources <- datasourcePageQueryResult
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalDatasources
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse(totalDatasources,
        hasPrevious,
        hasNext,
        pageRequest.offset,
        pageRequest.limit,
        datasources)
    }
  }

  /** Insert a datasource given a create case class with a user
    *
    * @param datasourceToCreate Datasource.Create object to use to create full datasource
    * @param user               User to create a new datasource with
    */
  def insertDatasource(datasourceToCreate: Datasource.Create, user: User)
                      (implicit database: DB): Future[Datasource] = {

    val datasource = datasourceToCreate.toDatasource(user.id)
    val insertAction = Datasources.forceInsert(datasource)

    database.db.run {
      insertAction
    } map { _ =>  datasource }
  }


  /** Given a datasource ID, attempt to retrieve it from the database
    *
    * @param datasourceId UUID ID of datasource to get from database
    */
  def getDatasource(datasourceId: UUID)(implicit database: DB): Future[Option[Datasource]] = {
    val fetchAction = Datasources.filter(_.id === datasourceId).result.headOption

    database.db.run {
      fetchAction
    }
  }

  /** Given a datasource ID, attempt to remove it from the database
    *
    * @param datasourceId UUID ID of datasource to remove
    */
  def deleteDatasource(datasourceId: UUID)(implicit database: DB): Future[Int] = {
    val deleteAction = Datasources.filter(_.id === datasourceId).delete

    database.db.run {
      deleteAction
    }
  }

  /** Update a datasource
    * @param datasource Datasource to use for update
    * @param datasourceId UUID of datasource to update
    * @param user User to use to update datasource
    */
  def updateDatasource(datasource: Datasource, datasourceId: UUID, user: User)(implicit database: DB): Future[Int] = {
    val updateTime = new Timestamp((new java.util.Date).getTime)

    val updateDatasourceQuery = for {
      updateDatasource <- Datasources.filter(_.id === datasourceId)
    } yield (
      updateDatasource.modifiedAt,
      updateDatasource.modifiedBy,
      updateDatasource.organizationId,
      updateDatasource.name,
      updateDatasource.visibility,
      updateDatasource.colorCorrection,
      updateDatasource.extras
    )

    database.db.run {
      updateDatasourceQuery.update((
        updateTime,
        user.id,
        datasource.organizationId,
        datasource.name,
        datasource.visibility,
        datasource.colorCorrection,
        datasource.extras
      ))
    } map {
      case 1 => 1
      case _ => throw new IllegalStateException("Error while updating datasource")
    }
  }
}

class DatasourceTableQuery[M, U, C[_]](datasources: Datasources.TableQuery) {
  def page(pageRequest: PageRequest): Datasources.TableQuery = {
    Datasources
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }
}

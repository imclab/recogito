package models

import play.api.Play.current
import play.api.db.slick._
import play.api.db.slick.Config.driver.simple._
import scala.slick.lifted.Tag

/** Geospatial Document source text case class.
  *
  * @author Rainer Simon <rainer.simon@ait.ac.at>
  */
case class GeoDocumentText(id: Option[Int] = None, gdocId: Int, gdocPartId: Option[Int], text: Array[Byte])

class GeoDocumentTexts(tag: Tag) extends Table[GeoDocumentText](tag, "gdocument_texts") {

  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  
  def gdocId = column[Int]("gdoc")
  
  def gdocPartId = column[Int]("gdoc_part", O.Nullable)
  
  def text = column[Array[Byte]]("text")
  
  def * = (id.?, gdocId, gdocPartId.?, text) <> (GeoDocumentText.tupled, GeoDocumentText.unapply)
  
}
    
object GeoDocumentTexts {   
  
  private val query = TableQuery[GeoDocumentTexts]
  
  def create()(implicit s: Session) = query.ddl.create
  
  def insert(geoDocumentText: GeoDocumentText)(implicit s: Session) = query.insert(geoDocumentText)
  
  /** Retrieve a text with the specified ID (= primary key) **/
  def findById(id: Int)(implicit s: Session): Option[GeoDocumentText] =
    query.where(_.id === id).firstOption
    
    
    
  /** Deletes texts associated with a GeoDocument **/  
  def deleteForGeoDocument(id: Int)(implicit s: Session) =
    query.where(_.gdocId === id).delete
    
    
    
  /** Retrieves all texts associated with a specific GeoDocument (or parts of it) **/
  def findByGeoDocument(gdocId: Int)(implicit s: Session): Seq[GeoDocumentText] =
    query.where(_.gdocId === gdocId).list
    
    
    
  /** Retrieves the text that is **directly** associated with the specified GeoDocument.
    * 
    * Note that this method will **not** retrieve texts that are associated with parts of
    * the specified GeoDocument.  
    */
  def getTextForGeoDocument(gdocId: Int)(implicit s: Session): Option[GeoDocumentText] =
    query.where(_.gdocId === gdocId).filter(_.gdocPartId.isNull).firstOption
    
  /** Retrieves the text that is associated with the specified GeoDocument part **/
  def getTextForGeoDocumentPart(gdocPartId: Int)(implicit s: Session): Option[GeoDocumentText] =
    query.where(_.gdocPartId === gdocPartId).firstOption
    
  /** Retrieves the text that is associated with the specified annotation **/
  def getTextForAnnotation(annotation: Annotation)(implicit s: Session): Option[GeoDocumentText] =    
    if (annotation.gdocPartId.isDefined)
      query.where(_.gdocPartId === annotation.gdocPartId.get).firstOption
    else
      query.where(_.gdocId === annotation.gdocId).filter(_.gdocPartId.isNull).firstOption
  
}
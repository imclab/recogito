package controllers.admin

import controllers.{ Secure, Secured }
import controllers.common.io.{ CSVParser, ZipExporter, ZipImporter }
import java.util.zip.ZipFile
import models._
import play.api.mvc.Controller
import play.api.db.slick._
import play.api.db.slick.Config.driver.simple._
import play.api.Play.current
import play.api.Logger
import scala.io.Source
import play.api.data.Form
import play.api.data.Forms._

/** Controller for the 'Documents' section of the admin area.
  * 
  * @author Rainer Simon <rainer.simon@ait.ac.at> 
  */
object DocumentAdminController extends Controller with Secured {
  
  /** The document form (used in both create and edit documents) **/
  private val documentForm = Form(
	mapping(
		"id" -> optional(number),
		"extWorkID" -> optional(text),
		"author" -> optional(text), 
		"title" -> text, 
		"date" -> optional(number), 
		"dateComment" -> optional(text),
		"language" -> optional(text),
		"description" -> optional(text),
		"source" -> optional(text)
	)(GeoDocument.apply)(GeoDocument.unapply)
  )
  
  /** Index page listing all documents in the DB **/
  def listAll = adminAction { username => implicit session =>
    Ok(views.html.admin.documents(GeoDocuments.listAll()))
  }
  
  /** Download one specific document (with text and annotations) as a ZIP file **/
  def downloadDocument(gdocId: Int) = adminAction { username => implicit session =>
    val gdoc = GeoDocuments.findById(gdocId)
    if (gdoc.isDefined) {
      val exporter = new ZipExporter()
      val file = exporter.exportGDoc(gdoc.get)
      val bytes = Source.fromFile(file.file)(scala.io.Codec.ISO8859).map(_.toByte).toArray
      file.finalize()
      Ok(bytes).withHeaders(CONTENT_TYPE -> "application/zip", CONTENT_DISPOSITION -> ({ "attachment; filename=" + exporter.escapeTitle(gdoc.get.title) }))
    } else {
      NotFound
    }
  }
    
  /** Delete a document (and associated data) from the database **/
  def deleteDocument(id: Int) = protectedDBAction(Secure.REJECT) { username => implicit session =>
    Annotations.deleteForGeoDocument(id)
    GeoDocumentTexts.deleteForGeoDocument(id)
    GeoDocumentParts.deleteForGeoDocument(id)    
    GeoDocuments.delete(id)
    Status(200)
  }
  
  /** Upload documents (with text and annotations) to the database, from a ZIP file **/
  def uploadDocuments = adminAction { username => implicit session =>
    val formData = session.request.body.asMultipartFormData
    if (formData.isDefined) {
      try {
        val f = formData.get.file("zip")
        if (f.isDefined) {
          val zipFile = new ZipFile(f.get.ref.file)
          val errors = ZipImporter.validateZip(zipFile)
          if (errors.size == 0) {
            val imported = ZipImporter.importZip(zipFile)
            Redirect(routes.DocumentAdminController.listAll).flashing("success" -> { "Uploaded " + imported + " document(s)." })
          } else {
            Redirect(routes.DocumentAdminController.listAll).flashing("error" -> { "<strong>Your file had " + errors.size + " errors:</strong>" + errors.map("<p>" + _ + "</p>").mkString("\n") })
          }
        } else {
          BadRequest
        }
      } catch {
        case t: Throwable => Redirect(routes.DocumentAdminController.listAll).flashing("error" -> { "There is something wrong with your JSON: " + t.getMessage })
      }
    } else {
      BadRequest
    }
  }
  
  /** Create a new document in the UI form 
  def createDocument = adminAction { username => implicit session =>
    Ok(views.html.admin.documentEditForm(documentForm))
  }**/
  
  /** Edit an existing document in the UI form **/
  def editDocument(id: Int) = DBAction { implicit session =>
    GeoDocuments.findById(id).map { doc =>
      Ok(views.html.admin.documentEditForm(id, documentForm.fill(doc)))
    }.getOrElse(NotFound)
  }
  
  /** Save a document from the UI form **/
  def updateDocument(id: Int) = protectedDBAction(Secure.REJECT) { implicit request => implicit session =>
    documentForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.documentEditForm(id, formWithErrors)),
      document => {
        GeoDocuments.update(document)
        Redirect(routes.DocumentAdminController.listAll())
      }
    )
  }  
  
  /** Import annotations from a CSV file into the document with the specified ID **/
  def uploadAnnotations(doc: Int) = DBAction(parse.multipartFormData) { implicit session =>
    val gdoc = GeoDocuments.findById(doc)
    if (gdoc.isDefined) {
      session.request.body.file("csv").map(filePart => {
        val parser = new CSVParser()
        val annotations = parser.parseAnnotations(filePart.ref.file.getAbsolutePath, gdoc.get.id.get)
        Logger.info("Importing " + annotations.size + " annotations to " + gdoc.get.title)
        Annotations.insertAll(annotations)
      })
    }
    Redirect(routes.DocumentAdminController.listAll)
  }
  
  /** Drop all annotations from the document with the specified ID **/
  def deleteAnnotations(doc: Int) = adminAction { username => implicit session =>
    Annotations.deleteForGeoDocument(doc)
    Status(200)
  }
  
}
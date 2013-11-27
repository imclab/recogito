package controllers

import models._
import play.api.db.slick._
import play.api.db.slick.Config.driver.simple._
import play.api.Play.current
import play.api.mvc.{ Action, Controller }
import play.api.libs.json.Json
import java.sql.Timestamp
import java.util.Date
import play.api.Logger

/** Controller for the edit API.
  *
  * Note that the API sits behind the login wall (while read API access is 
  * available to everyone).  
  */
object Edit extends Controller with Secured {
  
  /** Updates the annotation with the specified ID **/
  def updateAnnotation(id: Int) = apiWithAuth { username => implicit requestWithSession =>
    val annotation = Annotations.findById(id)
    val user = Users.findByUsername(username)
    
    if (annotation.isDefined && user.isDefined) {
      // Update the annotation
      val body = requestWithSession.request.body
      val toponym = (body \ "toponym").as[String]
      val status = AnnotationStatus.withName((body \ "status").as[String])
      val fix = (body \ "fix").as[Option[String]]
   
      val updatedAnnotation = Annotation(Some(id), annotation.get.gdocId, annotation.get.gdocPartId, status, annotation.get.toponym,
          annotation.get.offset, annotation.get.gazetteerURI, None, None, fix, annotation.get.comment)
          
      Annotations.update(updatedAnnotation)

      // Create a record in the edit history
      EditHistory.insert(createEvent(annotation.get, updatedAnnotation, user.get.id.get))
      Ok(Json.obj("msg" -> "ack"))
    } else {
      val msg = if (user.isEmpty) "Not logged in" else "No annotation with ID " + id
      NotFound(Json.obj("error" -> msg))
    }
  }
  
  /** Creates an update event by comparing original and update annotation **/
  private def createEvent(original: Annotation, updated: Annotation, userId: Int): EditEvent = {
    val updatedStatus = if (original.status.equals(updated.status)) None else Some(updated.status)
    val updatedURI = if (original.correctedGazetteerURI.equals(updated.correctedGazetteerURI)) None else updated.correctedGazetteerURI
    EditEvent(None, original.id.get, userId, new Timestamp(new Date().getTime), None, updatedStatus, updatedURI, None)
  }

}
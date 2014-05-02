package uk.gov.tna.dri.preingest.loader

import akka.actor.{ActorRef, ActorSystem, Props, Actor}
import akka.pattern._
import akka.util.Timeout
import org.scalatra._
import org.scalatra.scalate.ScalateSupport
import uk.gov.tna.dri.preingest.loader.auth.{User, LDAPAuthenticationSupport}
import org.scalatra.atmosphere._
import org.scalatra.json.{JValueResult, JacksonJsonSupport}
import org.json4s._
import JsonDSL._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import grizzled.slf4j.Logging
import uk.gov.tna.dri.preingest.loader.unit._
import org.scalatra.servlet.FileUploadSupport
import uk.gov.tna.dri.preingest.loader.certificate.CertificateList
import org.scalatra.atmosphere.Disconnected
import org.scalatra.atmosphere.JsonMessage
import org.scalatra.atmosphere.TextMessage
import scala.Some
import uk.gov.tna.dri.preingest.loader.certificate.ListCertificates
import org.scalatra.atmosphere.Error
import uk.gov.tna.dri.preingest.loader.certificate.StoreCertificates


class PreIngestLoader(system: ActorSystem, preIngestLoaderActor: ActorRef, certificateManagerActor: ActorRef) extends ScalatraServlet
  with ScalateSupport with JValueResult
  with JacksonJsonSupport
  with SessionSupport
  with FileUploadSupport
  with AtmosphereSupport
  with FutureSupport
  with LDAPAuthenticationSupport
  with Logging {

  protected val settings = Settings(system)

  implicit protected val jsonFormats: Formats = DefaultFormats
  protected implicit def executor: ExecutionContext = system.dispatcher

  def toJson(cl: CertificateList) : JValue = {
    ("certificate" ->
      cl.certificates.map{
        certificate =>
          ("name" -> certificate)
      }
    )
  }

  import org.scalatra.ActionResult._

  get("/") {
    userPasswordAuth
    contentType = "text/html"
    ssp("home", "title" -> "DRI Pre-Ingest Loader", "username" -> user.username)
  }

  //TODO ideally would be implemented with web-sockets
  post("/certificate") {
    userPasswordAuth
    fileMultiParams.get("certificate") match {

      case Some(certificates) =>
        certificateManagerActor ! StoreCertificates(user.username, certificates.map(c => c.getName -> c.get)) //NOTE we extract the data with c.get here, otherwise jetty deletes the underlying file when the response is returned below
        Accepted()

      case None =>
        BadRequest(<p>No Certificate files were sent!</p>)
    }
  }


  //TODO ideally would be implemented with web-sockets
  get("/certificate") {
    userPasswordAuth
    new AsyncResult {
      val is = ask(certificateManagerActor, ListCertificates(user.username))(Timeout(10 seconds)).mapTo[CertificateList].map(toJson(_))
    }
  }

  /* start security uri */
  get("/login") {
    contentType = "text/html"
    ssp("login", "title" -> "DRI Login")
  }

  post("/login") {
    userPasswordAuth
    redirect("/")
  }

  get("/logout") {
    logOut
    redirect("/")
  }
  /* end security uri */

//TODO trying to use atmosphere for file upload, needs Scalatra 2.3.x (Adam sent them a pull-request!)
//  atmosphere("/certificate/:filename") {
//    val filename = params("filename")
//
//    new AtmosphereClient {
//      def receive = {
//        case TextMessage(text) if(!text.isEmpty) =>
//          val x = text.getBytes()
//          println(s"filename: ${filename} Received text: $text")
//      }
//    }
//  }

  atmosphere("/unit") {
    userPasswordAuth
    val x: User = user  //TODO this is not very safe! what happens when the user's session expires etc!

    new AtmosphereClient {
       def receive = {

         case Connected =>
          info(s"Client $uuid is connected")

         case Disconnected(disconnector, Some(error)) =>
          warn(s"Client $uuid disconnected: " +  error)

         case Error(Some(err)) =>
          error(err)

         case TextMessage(text) =>
           debug("RECEIVED TEXT: " + text)

         case JsonMessage(json) =>
          debug("RECEIVED JSON: " + json)

          //val username = user.username //TODO causes NPE at the moment
          val username = x.username //TODO fix above, this is a temp solution

          import uk.gov.tna.dri.preingest.loader.ClientAction.{Decrypt, Pending, UnitRef, Load}
          val clientAction = json.extractOpt[Load].orElse(json.extractOpt[Decrypt].orElse(json.extractOpt[Pending]))
          clientAction match {

            case Some(p: Pending) =>
              preIngestLoaderActor ! ListUnits(uuid)

            case Some(Decrypt(_, UnitRef(uid), certificate, passphrase)) =>
              preIngestLoaderActor ! UpdateUnitDecryptDetail(username, uid, certificate, passphrase, Option(uuid))

            case Some(l: Load) =>
              val parts = l.unit.parts.map(p => TargetedPart(Destination.withName(p.destination), Part(p.unit, p.series)))
              preIngestLoaderActor ! LoadUnit(username, l.unit.uid, parts, l.certificate, l.passphrase, Option(uuid))

            case None =>
              error("Unknown Client Action!")
          }
       }
    }
  }
}

class PreIngestLoaderActor extends Actor with Logging {

  val unitManagerActor = context.actorOf(Props[UnitManagerActor], name="unitManagerActor")
  unitManagerActor ! Listen

  def receive = {

    //send unit status (i.e. add of update)
    case UnitStatus(unit, action, clientId) =>
      AtmosphereClient.broadcast("/unit", JsonMessage(toJson("update", unit)), allOrOne(clientId))

    //remove unit detail
    case DeRegisterUnit(unitUid) =>
      AtmosphereClient.broadcast("/unit", JsonMessage(toJson("remove", unitUid)))

    //list all unit status
    case lu: ListUnits =>
      unitManagerActor ! lu

    case uudd: UpdateUnitDecryptDetail =>
      unitManagerActor ! uudd

    case lu: LoadUnit =>
      unitManagerActor ! lu
  }

  def allOrOne(maybeClientId: Option[String])(client: AtmosphereClient) : Boolean = {
    maybeClientId match {
      case Some(clientId) =>
        client.uuid == clientId
      case None =>
        true
    }
  }

  def toJson(action: String, unit: DRIUnit) : JObject = toJson(action, List(unit))

  def toJson(action: String, units: List[DRIUnit]) : JObject = {
    (action ->
      ("unit" ->
        units.map(_.toJson())
      )
    )
  }

  def toJson(action: String, unitUid: DRIUnit.UnitUID) = {
    (action ->
      ("unit" ->
        ("uid" -> unitUid)
      )
    )
  }


//  def toJson(uls: UnitLoadStatus) : JValue = {
//    ("loadStatus" ->
//      ("unit" ->
//        ("src" -> uls.src)
//      ) ~
//      ("status" -> uls.status.toString) ~
//      ("complete" -> uls.complete)
//    )
//  }
//
//  def toJson(action: String, pendingUnit: PendingUnit) : JValue = toJson(action, List(pendingUnit))
//
//  def toJson(action: String, pendingUnits: List[PendingUnit]) : JValue = {
//    (action ->
//      ("unit" ->
//        pendingUnits.map {
//          pendingUnit =>
//            ("interface" -> pendingUnit.interface) ~
//            ("src" -> pendingUnit.src) ~
//            ("label" -> pendingUnit.label) ~
//            ("size" -> pendingUnit.size) ~
//            ("timestamp" -> pendingUnit.timestamp) ~
//            ("part" ->
//              pendingUnit.parts.map {
//                _.map {
//                  part =>
//                    ("unit" -> part.unitId) ~
//                    ("series" -> part.series)
//                }
//              }
//            )
//        }
//      )
//    )
//  }
}
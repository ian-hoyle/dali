package uk.gov.tna.dri.preingest.loader.unit.disk

import uk.gov.tna.dri.preingest.loader.unit._
import uk.gov.tna.dri.preingest.loader.certificate.CertificateDetail
import uk.gov.tna.dri.preingest.loader.unit.DRIUnit._
import akka.actor.{Props, ActorRef}
import scala.util.control.Breaks._
import grizzled.slf4j.Logger
import uk.gov.tna.dri.preingest.loader.unit.common.MediaUnitActor
import uk.gov.tna.dri.preingest.loader.unit.PartitionDetailsForEncryptionMethodChange
import uk.gov.tna.dri.preingest.loader.unit.SendPartitionDetails
import uk.gov.tna.dri.preingest.loader.unit.TargetedPart


// This actor is a placeholder which is not allowed to execute any processing; the user must replace it by fixing a specific encryption method
class EncryptedPartitionUnitActor(var unit: EncryptedPartitionUnit) extends MediaUnitActor[EncryptedPartitionUnit] with EncryptedDRIUnitActor[EncryptedPartitionUnit] {

  receiveBuilder += {

    // used only by UnitManagerActor when replacing encryption method actor
    case SendPartitionDetails(manager: ActorRef, unitId: UnitUID, encryptionMethod: Option[String]) =>
      manager ! PartitionDetailsForEncryptionMethodChange(unitId, encryptionMethod, unit)
  }

  // All methods are stubs only; they are not expected to be called

  def fixityCheck(username: String, part: TargetedPart, passphrase: Option[String], unitManager: Option[ActorRef]) = ()

  def copyData(username: String, parts: Seq[TargetedPart], passphrase: Option[String], unitManager: Option[ActorRef]) = ()

  def copyData(username: String, parts: Seq[TargetedPart], certificate: CertificateDetail, passphrase: Option[String], unitManager: Option[ActorRef]) = ()

  def updateDecryptDetail(username: String, passphrase: String) = ()

  def updateDecryptDetail(username: String, listener: ActorRef, cert: CertificateDetail, passphrase: String) = ()

}

// See http://doc.akka.io/docs/akka/snapshot/scala/actors.html : Recommended Practices
object EncryptedPartitionUnitActor {
  def props(unit: EncryptedPartitionUnit): Props = Props(new EncryptedPartitionUnitActor(unit))
}

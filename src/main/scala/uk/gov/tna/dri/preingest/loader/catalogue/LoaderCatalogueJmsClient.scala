package uk.gov.tna.dri.preingest.loader.catalogue

import uk.gov.tna.dri.catalogue.jms.client.{JmsConfig, CatalogueJmsClient}
import javax.xml.bind.JAXBElement
import uk.gov.nationalarchives.dri.ingest.DriUnitsType
import akka.actor.Actor
import uk.gov.tna.dri.preingest.loader.unit.GetLoaded

/**
 * Created with IntelliJ IDEA.
 * User: Rob Walpole
 * Date: 5/22/14
 * Time: 10:09 AM
 */
class LoaderCatalogueJmsClient(jmsConfig: JmsConfig) extends CatalogueJmsClient(jmsConfig) {

  def getUnitsLoaded(limit: Int): Option[JAXBElement[DriUnitsType]] = {
    logger.info("Retrieving " + limit + " loaded units.")
    val xml = populateXml("getUnitsLoaded", getQuery(10), limitParam)
    val reply = exchangeMessages(xml, jmsConfig)
    reply match {
      case Right(x) => {
        Some(x.getAny.asInstanceOf[JAXBElement[DriUnitsType]])
      }
      case Left(x) => {
        logger.error("Retrieving loaded units from catalogue returned: " + x)
        None
      }
    }
  }
}

//class CatalogueActor extends Actor {
//
//  def receive = {
//
//    case GetLoaded(limit) =>
//     val jmsClient = new LoaderCatalogueJmsClient()
//     jmsClient.getUnitsLoaded(limit)
//     // what's the message? i.e. case
//    // do something
//    // send message back (or somewhere)
//    ???
//  }
//
//}

package uk.gov.tna.dri.preingest.loader

import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem

import com.typesafe.config.Config
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import uk.gov.tna.dri.preingest.loader.Crypto.DigestAlgorithm
import scalax.file.Path
import scala.collection.JavaConverters._

class SettingsImpl(config: Config) extends Extension {

  object Auth {

    val ldapServer = config.getStringList("unit-loader.auth.ldap.server").asScala.toList
    val ldapPort = config.getInt("unit-loader.auth.ldap.port")
    val ldapBindUser = config.getString("unit-loader.auth.ldap.bind.user")
    val ldapBindPassword = config.getString("unit-loader.auth.ldap.bind.password")
    val ldapSearchBase = config.getString("unit-loader.auth.ldap.search-base")
    val ldapUserObjectClass = config.getString("unit-loader.auth.ldap.user.object-class")
    val ldapUserAttributeDN = config.getString("unit-loader.auth.ldap.user.attribute.dn")
    val ldapUserAttributeEmail = config.getString("unit-loader.auth.ldap.user.attribute.email")
    val ldapUserAttributeUid = config.getString("unit-loader.auth.ldap.user.attribute.uid")
    val ldapUserAttributeGroupMembership = config.getString("unit-loader.auth.ldap.user.attribute.group-membership")
    val ldapApplicationGroup = config.getString("unit-loader.auth.ldap.application-group")
    val ldapTimeoutConnection = config.getInt("unit-loader.auth.ldap.timeout.connection")
    val ldapTimeoutRequest = config.getInt("unit-loader.auth.ldap.timeout.request")

    val rememberMeCookieKey = config.getString("unit-loader.auth.remember-me.cookie.key")
    val rememberMeCookieLifetime = Duration(config.getMilliseconds("unit-loader.auth.remember-me.cookie.lifetime"), TimeUnit.MILLISECONDS)
  }

  object CertificateManager {
    val encryptedFileExtension = config.getString("unit-loader.certificate-manager.encrypted-file-extension")
    val digestAlgorithm = DigestAlgorithm.withName(config.getString("unit-loader.certificate-manager.digest-algorithm"))
  }

  object DataStore {
    val userData = Path.fromString(sys.props("user.home")) / config.getString("unit-loader.data-store.user-data")
    val digestAlgorithm = DigestAlgorithm.withName(config.getString("unit-loader.data-store.digest-algorithm"))
  }

  object Unit {
    val uploadedScheduleDelay = Duration(config.getMilliseconds("unit-loader.unit.manager.uploaded-check-schedule.delay"), TimeUnit.MILLISECONDS)
    val uploadedScheduleFrequency = Duration(config.getMilliseconds("unit-loader.unit.manager.uploaded-check-schedule.frequency"), TimeUnit.MILLISECONDS)

    val uploadedInterface = config.getString("unit-loader.unit.uploaded.interface")
    val uploadedSource = Path.fromString(config.getString("unit-loader.unit.uploaded.source"))

    val uploadedUidGenDigestAlgorithm = DigestAlgorithm.withName(config.getString("unit-loader.unit.uploaded.uid-gen-digest-algorithm"))

    val uploadedGpgZipFileExtension = config.getString("unit-loader.unit.uploaded.gpg-zip-file-extension")

    val junkFiles = config.getStringList("unit-loader.unit.junk-files").asScala.map(_.r)

    val destination = Path.fromString(config.getString("unit-loader.unit.destination"))
  }

  object DBus {
    val udisksBusName = config.getString("unit-loader.dbus.udisks.bus-name")
    val udisksPath = config.getString("unit-loader.dbus.udisks.path")
    val udisksIgnoreDevices = config.getStringList("unit-loader.dbus.udisks.ignore-devices").asScala.map(_.r)
  }

  object Truecrypt {
    val bin = Path.fromString(config.getString("unit-loader.truecrypt.bin"))
  }

  object NtfsProgs {
    val labelBin = Path.fromString(config.getString("unit-loader.ntfs.label-bin"))
  }

}

object Settings extends ExtensionId[SettingsImpl] with ExtensionIdProvider {

  override def lookup = Settings

  override def createExtension(system: ExtendedActorSystem) =
    new SettingsImpl(system.settings.config)

  /**
   * Java API: retrieve the Settings extension for the given system.
   */
  override def get(system: ActorSystem): SettingsImpl = super.get(system)
}

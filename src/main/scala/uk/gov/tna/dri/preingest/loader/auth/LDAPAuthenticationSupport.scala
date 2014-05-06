package uk.gov.tna.dri.preingest.loader.auth

import org.scalatra.auth.{ScentryConfig, ScentrySupport}
import org.scalatra.ScalatraBase
import uk.gov.tna.dri.preingest.loader.SettingsImpl

trait LDAPAuthenticationSupport extends ScentrySupport[User]
  with UserPasswordAuthSupport[User]
  with LDAPUserManager {
  self: ScalatraBase =>

  protected val settings: SettingsImpl

  protected def fromSession = {
    case id: String =>
      find(id).getOrElse(null)
  }
  protected def toSession = {
    case user: User =>
      user.id
  }
  protected val scentryConfig = (new ScentryConfig{}).asInstanceOf[ScentryConfiguration]

  override protected def configureScentry = {
    scentry.unauthenticated {
      scentry.strategies(UserPasswordStrategy.STRATEGY_NAME).unauthenticated()
    }
  }

  override protected def registerAuthStrategies = {
    scentry.register(new UserPasswordStrategy(self, settings))
    scentry.register(new RememberMeStrategy(self, settings))
  }

}

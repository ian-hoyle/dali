/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.unit.common
import uk.gov.nationalarchives.dri.preingest.loader.SettingsImpl

package object unit {

  def isJunkFile(settings: SettingsImpl, name: String) = settings.Unit.junkFiles.find(_.findFirstMatchIn(name).nonEmpty).nonEmpty

}

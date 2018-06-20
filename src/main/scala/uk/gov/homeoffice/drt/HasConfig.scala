package uk.gov.homeoffice.drt

import com.typesafe.config.ConfigFactory

trait HasConfig {
  implicit val config = ConfigFactory.load
}

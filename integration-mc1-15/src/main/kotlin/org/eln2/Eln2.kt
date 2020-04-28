package org.eln2;

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object Eln2 {
	// Directly reference a log4j logger.
	@JvmField
	val LOGGER: Logger = LogManager.getLogger()
}

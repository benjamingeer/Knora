"""Primary location for setting Knora-API project wide versions"""

SCALA_VERSION = "2.13.7"
AKKA_VERSION = "2.6.17"
AKKA_HTTP_VERSION = "10.2.4"
JENA_VERSION = "3.14.0"
METRICS_VERSION = "4.0.1"

# SIPI - digest takes precedence!
SIPI_REPOSITORY = "daschswiss/sipi"
SIPI_VERSION = "3.3.1"
SIPI_IMAGE = SIPI_REPOSITORY
SIPI_IMAGE_DIGEST = "sha256:67a0e8c16a67914f2765a1c7906e781383b835cfe72cd19c763b7bc9eb7a38a5"

# Jena Fuseki - digest takes precedence!
FUSEKI_REPOSITORY = "daschswiss/apache-jena-fuseki"
FUSEKI_VERSION = "2.0.7"  # contains Fuseki 4.3.2 (with log4j 2.16.0)
FUSEKI_IMAGE = FUSEKI_REPOSITORY
FUSEKI_IMAGE_DIGEST_AMD64 = "sha256:fea8c556fca76c6beab2589018babd778d55dbdffef725fd2de784646cca0638"
FUSEKI_IMAGE_DIGEST_ARM64 = "sha256:137dc481978e13ff56a7212976b2ef0d8696e08c0abf4f97a4973a2af0b77b5a"

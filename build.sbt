import org.nlogo.build.{ ExtensionDocumentationPlugin, NetLogoExtension }

enablePlugins(NetLogoExtension, ExtensionDocumentationPlugin)

name       := "matrix"
version    := "1.1.2"
isSnapshot := true

netLogoClassManager := "org.nlogo.extensions.matrix.MatrixExtension"
netLogoVersion      := "7.0.0-beta1"

scalaVersion        := "3.7.0"
Test / scalaSource := baseDirectory.value / "src" / "test"
scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings", "-encoding", "us-ascii", "-release", "11")

Compile / javaSource := baseDirectory.value / "src" / "main"
javacOptions  ++= Seq("-g", "-deprecation", "-encoding", "us-ascii", "--release", "11")

libraryDependencies ++= Seq(
  "gov.nist.math" % "jama" % "1.0.3"
)

import org.nlogo.build.{ ExtensionDocumentationPlugin, NetLogoExtension }

enablePlugins(NetLogoExtension, ExtensionDocumentationPlugin)

name := "matrix"

version := "1.1.2"

netLogoClassManager := "org.nlogo.extensions.matrix.MatrixExtension"

netLogoTarget :=
  NetLogoExtension.directoryTarget(baseDirectory.value)

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings",
  "-encoding", "us-ascii")

javacOptions ++= Seq("-g", "-deprecation", "-Xlint:all", "-Xlint:-serial", "-Xlint:-path",
  "-encoding", "us-ascii")

libraryDependencies ++= Seq(
  "gov.nist.math" % "jama" % "1.0.3",
  "com.typesafe" % "config" % "1.3.1" % "test",
  "org.ow2.asm" % "asm-all" % "5.0.3" % "test",
  "org.scala-lang" % "scala-library" % "2.12.0" % "test",
  "org.picocontainer" % "picocontainer" % "2.13.6" % "test",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4" % "test",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test")

test in Test := {
  // This way of running tests is *crazy*, but it's how to get this
  // to work with ExtensionManager -sigh-  RG 8/26/15
  val testDir = IO.createTemporaryDirectory
  val matrixDir = testDir / "extensions" / "matrix"
  IO.createDirectory(matrixDir)
  val matrixJar = (packageBin in Compile).value
  val testFile  = baseDirectory.value / "tests.txt"
  IO.copy(Seq(
    matrixJar -> matrixDir / matrixJar.getName,
    testFile  -> matrixDir / testFile.getName
  ))
  val forkOptions = ForkOptions().withWorkingDirectory(Some(testDir))
  val allDependencies = Attributed.data((dependencyClasspath in Compile).value ++ (dependencyClasspath in Test).value)
  val netLogoTestsJar = allDependencies.find(j => (j.getName.contains("netlogo") || j.getName.contains("NetLogo")) &&
    j.getName.contains("test")).get
  val classpath = (allDependencies :+ scalaInstance.value.libraryJar).map(_.getPath).mkString(":")
  val testProcess = Fork.java.fork(forkOptions, Seq(
    "-classpath", classpath,
    "-Djava.awt.headless=true",
    "org.scalatest.tools.Runner",
    "-o",
    "-R", netLogoTestsJar.getPath,
    "-s", "org.nlogo.headless.TestExtensions"))
  val wasSuccess = testProcess.exitValue()
  if (wasSuccess != 0)
    sys.error("tests did not complete successfully")
}

resolvers      += "netlogo" at "https://dl.cloudsmith.io/public/netlogo/netlogo/maven/"
netLogoVersion := "6.2.0-d27b502"
isSnapshot := true

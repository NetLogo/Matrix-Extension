import org.nlogo.build.NetLogoExtension

enablePlugins(NetLogoExtension)

scalaVersion := "2.11.7"

name := "matrix"

netLogoClassManager := "org.nlogo.extensions.matrix.MatrixExtension"

netLogoTarget :=
  NetLogoExtension.directoryTarget(baseDirectory.value)

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings",
  "-encoding", "us-ascii")

javacOptions ++= Seq("-g", "-deprecation", "-Xlint:all", "-Xlint:-serial", "-Xlint:-path",
  "-encoding", "us-ascii")

val netLogoJarURL =
  Option(System.getProperty("netlogo.jar.url")).getOrElse("http://ccl.northwestern.edu/netlogo/5.3.0/NetLogo.jar")

val netLogoJarsOrDependencies = {
  import java.io.File
  import java.net.URI
  val urlSegments = netLogoJarURL.split("/")
  val lastSegment = urlSegments.last.replaceFirst("NetLogo", "NetLogo-tests")
  val testsUrl = (urlSegments.dropRight(1) :+ lastSegment).mkString("/")
  if (netLogoJarURL.startsWith("file:"))
    Seq(unmanagedJars in Compile ++= Seq(
      new File(new URI(netLogoJarURL)), new File(new URI(testsUrl))))
  else
    Seq(libraryDependencies ++= Seq(
      "org.nlogo" % "NetLogo" % "5.3.0" from netLogoJarURL,
      "org.nlogo" % "NetLogo-tests" % "5.3.0" % "test" from testsUrl))
}

netLogoJarsOrDependencies

libraryDependencies ++= Seq(
  "gov.nist.math" % "jama" % "1.0.3",
  "org.ow2.asm" % "asm-all" % "5.0.3" % "test",
  "org.scala-lang" % "scala-library" % "2.11.6" % "test",
  "org.picocontainer" % "picocontainer" % "2.13.6" % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test")

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
  val forkOptions = ForkOptions(workingDirectory = Some(testDir))
  val allDependencies = Attributed.data((dependencyClasspath in Compile).value ++ (dependencyClasspath in Test).value)
  val netLogoTestsJar = allDependencies.find(_.getName == "NetLogo-tests.jar").get
  val classpath = allDependencies.map(_.getPath).mkString(":")
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

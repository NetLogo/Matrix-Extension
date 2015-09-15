scalaVersion := "2.11.7"

enablePlugins(org.nlogo.build.NetLogoExtension)

name := "matrix"

netLogoClassManager := "org.nlogo.extensions.matrix.MatrixExtension"

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings",
  "-encoding", "us-ascii")

javacOptions ++= Seq("-g", "-deprecation", "-Xlint:all", "-Xlint:-serial", "-Xlint:-path",
  "-encoding", "us-ascii")

val netLogoJarsOrDependencies =
  Option(System.getProperty("netlogo.jar.url"))
    .orElse(Some("http://ccl.northwestern.edu/netlogo/5.3.0/NetLogo.jar"))
    .map { url =>
      import java.io.File
      import java.net.URI
      val testsUrl = url.replaceFirst("NetLogo", "NetLogo-tests")
      if (url.startsWith("file:"))
        (Seq(new File(new URI(url)), new File(new URI(testsUrl))), Seq())
      else
        (Seq(), Seq(
          "org.nlogo" % "NetLogo" % "5.3.0" from url,
          "org.nlogo" % "NetLogo-tests" % "5.3.0" % "test" from testsUrl))
    }.get

unmanagedJars in Compile ++= netLogoJarsOrDependencies._1

libraryDependencies ++= netLogoJarsOrDependencies._2

libraryDependencies ++= Seq(
  "gov.nist.math" % "jama" % "1.0.3",
  "asm" % "asm-all" % "3.3.1" % "test",
  "org.scala-lang" % "scala-library" % "2.11.6" % "test",
  "org.picocontainer" % "picocontainer" % "2.13.6" % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
      )

packageBin in Compile := {
  val jar = (packageBin in Compile).value
  val matrixZip = baseDirectory.value / "matrix.zip"
  if (matrixZip.exists) {
    IO.unzip(matrixZip, baseDirectory.value)
    for (jar <- (baseDirectory.value / "matrix" ** "*.jar").get)
      IO.copyFile(jar, baseDirectory.value / jar.getName)
    IO.delete(baseDirectory.value / "matrix")
  }
  jar
}

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

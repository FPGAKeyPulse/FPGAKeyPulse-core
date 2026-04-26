ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "2.12.18"
ThisBuild / organization := "io.github.fpgakeypulse"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / publishMavenStyle := true

val githubPackagesRepository = "https://maven.pkg.github.com/fpgakeypulse/FPGAKeyPulse-core"

ThisBuild / publishTo := Some("github" at githubPackagesRepository)

ThisBuild / pomExtra :=
  <distributionManagement>
    <repository>
      <id>github</id>
      <name>GitHub FPGAKeyPulse Apache Maven Packages</name>
      <url>{githubPackagesRepository}</url>
    </repository>
  </distributionManagement>

ThisBuild / credentials ++= {
  for {
    actor <- sys.env.get("GITHUB_ACTOR")
    token <- sys.env.get("GITHUB_TOKEN")
  } yield Credentials("GitHub Package Registry", "maven.pkg.github.com", actor, token)
}.toSeq

val spinalVersion = "1.14.1"
val spinalCore = "com.github.spinalhdl" %% "spinalhdl-core" % spinalVersion
val spinalLib = "com.github.spinalhdl" %% "spinalhdl-lib" % spinalVersion
val spinalTester = "com.github.spinalhdl" %% "spinalhdl-tester" % spinalVersion
val spinalIdslPlugin = compilerPlugin(
  "com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % spinalVersion
)
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19"

lazy val commonSettings = Seq(
  homepage := Some(url("https://github.com/FPGAKeyPulse")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/FPGAKeyPulse/FPGAKeyPulse-core"),
      "scm:git@github.com:FPGAKeyPulse/FPGAKeyPulse-core.git"
    )
  ),
  developers := List(
    Developer(id = "xueweiwujxw", name = "Wlanxww", email = "xueweiwujxw@outlook.com", url = url("https://wlanxww.com"))
  ),
  licenses := Seq("BSD 3-Clause" -> new URL("https://opensource.org/licenses/BSD-3-Clause")),
  scalacOptions ++= Seq("-deprecation"),
  Test / fork := true
)

lazy val root = (project in file("."))
  .aggregate(core, tester)
  .settings(
    commonSettings,
    name := "fpga-keypulse",
    publish / skip := true
  )

lazy val core = (project in file("core"))
  .dependsOn(tester % "test->compile")
  .settings(
    commonSettings,
    name := "fpga-keypulse-core",
    description := "FPGA Key Pulse core",
    libraryDependencies ++= Seq(
      spinalCore,
      spinalLib,
      spinalIdslPlugin
    )
  )

lazy val tester = (project in file("tester"))
  .settings(
    commonSettings,
    name := "fpga-keypulse-tester",
    description := "FPGA Key Pulse tester",
    libraryDependencies ++= Seq(
      spinalCore,
      spinalLib,
      spinalTester,
      spinalIdslPlugin,
      scalaTest
    )
  )

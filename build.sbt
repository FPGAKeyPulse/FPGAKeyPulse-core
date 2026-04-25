ThisBuild / version := "0.1.7"
ThisBuild / scalaVersion := "2.12.18"
ThisBuild / organization := "io.github.fpgakeypulse"

publishMavenStyle := true

val spinalVersion = "1.14.1"
val spinalCore = "com.github.spinalhdl" %% "spinalhdl-core" % spinalVersion
val spinalLib = "com.github.spinalhdl" %% "spinalhdl-lib" % spinalVersion
val spinalIdslPlugin = compilerPlugin(
  "com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % spinalVersion
)

description := "FPGA Key Pulse core"
homepage := Some(url("https://github.com/FPGAKeyPulse"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/FPGAKeyPulse/FPGAKeyPulse-core"),
    "scm:git@github.com:FPGAKeyPulse/FPGAKeyPulse-core.git"
  )
)
developers := List(
  Developer(id = "xueweiwujxw", name = "Wlanxww", email = "xueweiwujxw@outlook.com", url = url("https://wlanxww.com"))
)
licenses := Seq("BSD 3-Clause" -> new URL("https://opensource.org/licenses/BSD-3-Clause"))

lazy val FPGAKeyPulseCore = (project in file("."))
  .settings(
    name := "fpga-keypulse-core",
    libraryDependencies ++= Seq(
      spinalCore,
      spinalLib,
      spinalIdslPlugin
    ),
    scalacOptions ++= Seq("-deprecation")
  )

fork := true
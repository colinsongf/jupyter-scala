import language.implicitConversions
import sbt._, Keys._
import sbtbuildinfo.Plugin._
import sbtrelease.ReleasePlugin._
import com.typesafe.sbt.pgp.PgpKeys

object JupyterScalaBuild extends Build {
  private val publishSettings = com.atlassian.labs.gitstamp.GitStampPlugin.gitStampSettings ++ Seq(
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    licenses := Seq("Apache License" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo := Some(ScmInfo(url("https://github.com/alexarchambault/jupyter-scala"), "git@github.com:alexarchambault/jupyter-scala.git")),
    pomExtra := {
      <url>https://github.com/alexarchambault/jupyter-scala</url>
      <developers>
        <developer>
          <id>alexarchambault</id>
          <name>Alexandre Archambault</name>
          <url>https://github.com/alexarchambault</url>
        </developer>
      </developers>
    },
    credentials += {
      Seq("SONATYPE_USER", "SONATYPE_PASS").map(sys.env.get) match {
        case Seq(Some(user), Some(pass)) =>
          Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
        case _ =>
          Credentials(Path.userHome / ".ivy2" / ".credentials")
      }
    },
    ReleaseKeys.versionBump := sbtrelease.Version.Bump.Bugfix,
    ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value
  )

  private val commonSettings = Seq(
    organization := "com.github.alexarchambault.jupyter",
    scalaVersion := "2.11.6",
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature"),
    resolvers ++= Seq(
      "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
      Resolver.sonatypeRepo("releases"),
      Resolver.sonatypeRepo("snapshots")
    ),
    scalacOptions += "-target:jvm-1.7",
    crossVersion := CrossVersion.full,
    crossScalaVersions := Seq("2.10.3", "2.10.4", "2.10.5", "2.11.0", "2.11.1", "2.11.2", "2.11.4", "2.11.5", "2.11.6"),
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
  ) ++ publishSettings

  private lazy val testSettings = Seq(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "utest" % "0.3.0" % "test"
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    fork in test := true,
    fork in (Test, test) := true,
    fork in (Test, testOnly) := true
  )

  private val ammoniteVersion = "0.3.1-SNAPSHOT"

  lazy val api = Project(id = "api", base = file("api"))
    .settings(commonSettings: _*)
    .settings(
      name := "jupyter-scala-api",
      libraryDependencies ++= Seq(
        "com.github.alexarchambault" %% "ammonite-api" % ammoniteVersion cross CrossVersion.full,
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        "com.lihaoyi" %% "ammonite-pprint" % "0.3.0"
      )
    )
    .settings(buildInfoSettings: _*)
    .settings(
      sourceGenerators in Compile <+= buildInfo,
      buildInfoKeys := Seq[BuildInfoKey](
        version,
        "ammoniteVersion" -> ammoniteVersion
      ),
      buildInfoPackage := "jupyter.scala"
    )

  lazy val kernel = Project(id = "kernel", base = file("kernel"))
    .dependsOn(api)
    .settings(commonSettings ++ testSettings: _*)
    .settings(
      name := "jupyter-scala",
      libraryDependencies ++= Seq(
        "com.github.alexarchambault.jupyter" %% "jupyter-kernel" % "0.2.0-SNAPSHOT",
        "com.github.alexarchambault" %% "ammonite-interpreter" % ammoniteVersion cross CrossVersion.full
      ),
      libraryDependencies ++= Seq(
        "com.github.alexarchambault.jupyter" %% "jupyter-kernel" % "0.2.0-SNAPSHOT",
        "com.github.alexarchambault" %% "ammonite-shell" % ammoniteVersion cross CrossVersion.full,
        "com.github.alexarchambault" %% "ammonite-spark_1.3" % ammoniteVersion cross CrossVersion.full
      ).map(_ % "test" classifier "tests")
    )

  lazy val cli = Project(id = "cli", base = file("cli"))
    .dependsOn(kernel)
    .settings(commonSettings: _*)
    .settings(xerial.sbt.Pack.packAutoSettings ++ xerial.sbt.Pack.publishPackTxzArchive ++ xerial.sbt.Pack.publishPackZipArchive: _*)
    .settings(
      name := "jupyter-scala-cli",
      libraryDependencies ++= Seq(
        "com.github.alexarchambault" %% "case-app" % "0.2.1",
        "ch.qos.logback" % "logback-classic" % "1.0.13"
      ),
      libraryDependencies ++= {
        if (scalaVersion.value startsWith "2.10.")
          Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full))
        else
          Seq()
      }
    )

  lazy val root = Project(id = "jupyter-scala", base = file("."))
    .settings(commonSettings: _*)
    .aggregate(api, kernel, cli)
}

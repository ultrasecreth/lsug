name := "lsug"

version := "0.1"

scalaVersion := "2.13.0"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core"               % "2.0.0-RC1",
  "org.scalatest" %% "scalatest"               % "3.0.8" % "test",
  "org.mockito"   %% "mockito-scala-scalatest" % "1.5.14" % "test",
  "org.mockito"   %% "mockito-scala-cats"      % "1.5.14" % "test"
)

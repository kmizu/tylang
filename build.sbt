ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .settings(
    name := "tylang",
    organization := "com.tylang",
    
    libraryDependencies ++= Seq(
      // ASM for bytecode generation
      "org.ow2.asm" % "asm" % "9.6",
      "org.ow2.asm" % "asm-tree" % "9.6",
      "org.ow2.asm" % "asm-util" % "9.6",
      
      // JLine3 for REPL
      "org.jline" % "jline-terminal" % "3.24.1",
      "org.jline" % "jline-reader" % "3.24.1",
      "org.jline" % "jline-builtins" % "3.24.1",
      
      // Testing
      "org.scalameta" %% "munit" % "0.7.29" % Test
    ),
    
    // Scala 3 compiler options
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:strictEquality"
    ),
    
    // Main class for running the REPL
    Compile / mainClass := Some("tylang.Main"),
    
    // Test framework
    testFrameworks += new TestFramework("munit.Framework"),
    
    // Assembly settings for creating fat JAR
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case x => MergeStrategy.first
    }
  )


package pl.project13.sbt.jcstress

import java.io.{File, IOException}

import sbinary.DefaultProtocol.StringFormat
import sbt.Attributed.data
import sbt.Def.Initialize
import sbt.KeyRanks._
import sbt.complete.{DefaultParsers, Parser}
import sbt.Task
import sbt._
import sbt.Keys._
import xsbt.api.Discovery

import scala.sys.process.ProcessLogger

object JCStressPlugin extends sbt.AutoPlugin {

  import autoImport._

  override def requires = sbt.plugins.JvmPlugin

  override def trigger = allRequirements

  override def projectSettings = Seq(
    version in Jcstress := "0.3",
    
    libraryDependencies += "org.openjdk.jcstress" % "jcstress-core" % (version in Jcstress).value % "test",
    libraryDependencies += "net.sf.jopt-simple"   % "jopt-simple"   % "4.6"   % "test",

    run in Jcstress :=
      Def.inputTask {
        import scala.sys.process._
        val log = streams.value.log

        val lm = {
          import sbt.librarymanagement.ivy._
          val ivyConfig = InlineIvyConfiguration().withLog(log)
          IvyDependencyResolution(ivyConfig)
        }
    
        // val ivy = ivySbt.value
        val jcstressVersion = (version in Jcstress).value
        val classpath = (fullClasspath in Test).value
        val args = Nil
        val prods = (products in Compile).value
        val dependenciesClasspath = (dependencyClasspath in Compile).value.files

        val logIt = new ProcessLogger {
          override def buffer[T](f: => T): T = f
          override def out(s: => String): Unit = log.info(s)
          override def err(s: => String): Unit = log.error(s)
        }

        val cpFiles = classpath.map(_.data)

        val jcstressDeps =
          getArtifact("org.openjdk.jcstress" % "jcstress-core" % jcstressVersion, lm, log) ::
            getArtifact("net.sf.jopt-simple" % "jopt-simple" % "4.6", lm, log) ::
            Nil

        val javaClasspath = jcstressDeps.mkString(":") + ":" + cpFiles.toList.mkString(":")

        val processor = "org.openjdk.jcstress.infra.processors.JCStressTestProcessor"
        val destinationDirectory = crossTarget.value / "test-classes" // FIXME hardcoded... 
        
        if (!destinationDirectory.exists()) destinationDirectory.mkdir()
        else if (destinationDirectory.isDirectory) () // good
        else throw new IOException(s"$destinationDirectory exists but is not a directory! Please delete it...")
            
        val classesToProcess = (sources in Test).value flatMap { file =>  
          file.relativeTo(baseDirectory.value / "src" / "test" / "scala")
        } map { relative => 
          relative.toString.replaceAll(".scala", "").replaceAll(File.separator, ".") // make it FQN names
        } 
        val processorClasspath = (javaClasspath :: (prods ++ dependenciesClasspath).toList).mkString(":")

        log.info("Generating sources and test list...")
        // TODO make it in one command?
          val genSources = s"javac -cp $processorClasspath -proc:only -processor $processor -d $destinationDirectory ${classesToProcess.mkString(" ")}"
          genSources.!!

        log.info("Compiling generated instrumented sources...")
        val generatedSourceNames = s"find ${crossTarget.value} -name *_jcstress.java".!!.split("\n")
        generatedSourceNames foreach { gendSource => 
          s"javac -cp $processorClasspath $gendSource".!!
        }
        
        log.info("Running tests...")
        try {
          val x = s"java -cp $javaClasspath org.openjdk.jcstress.Main ".!!(logIt)
          log.info(x)
        } finally {
          val resultsIndex = baseDirectory.value / "results" / "index.html"
          log.info(s"See results: $resultsIndex")
        }
      }
        .dependsOn(compile in Compile)
        .dependsOn(compile in Test) 
        .evaluated
  )  

  /** 
   * From typesafehub/migration-manager (apache v2 licensed).
   * Resolves an artifact representing the previous abstract binary interface for testing.
   */
  def getArtifact(module: ModuleID, lm:  sbt.librarymanagement.DependencyResolution, log: Logger): File = {
    val Right(files) = lm.retrieve(module, scalaModuleInfo = None, new File("target"), log)
    
    files.headOption getOrElse sys.error("Could not resolve jcstress artifact: " + module)
  }
  
  private def cpOption(cpFiles: Seq[File]): String = 
    "-cp " + cpFiles.mkString(":")  
  
//  private def discoverAllClasses(analysis: inc.Analysis): Seq[String] =
//    Discovery.applications(Tests.allDefs(analysis)).collect({ case (definition, discovered) => definition.name })

  private def runjcstressParser: (State, Seq[String]) => Parser[(String, Seq[String])] = {
    import DefaultParsers._
    (state, mainClasses) => Space ~> token(NotSpace examples mainClasses.toSet) ~ spaceDelimited("<arg>")
  }


  object autoImport {
    final val Jcstress = sbt.config("jcstress") extend sbt.Configurations.TestInternal  
  }

}

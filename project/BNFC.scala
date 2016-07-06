import sbt._
import Keys._

object BNFC {

  lazy val BNFCConfig     = config("bnfc")
  lazy val bnfcNamespace  = settingKey[String]("Namespace to prepend to the package/module name")
  lazy val bnfcGrammarDir = settingKey[File]("Directory for BNFC grammar files")
  lazy val bnfcOutputDir  = settingKey[File]("Directory for Java files generated by BNFC")
  lazy val bnfcDocDir     = settingKey[File]("Directory for LaTeX files generated by BNFC")
  lazy val generate       = taskKey[Unit]("Generates Java files from BNFC grammar files")
  lazy val cleanDocs      = taskKey[Unit]("Cleans BNFC-generated LaTeX files")
  lazy val generateDocs   = taskKey[Unit]("Generates LaTeX files from BNFC grammar files")

  def cleanDir(dir: File): Unit =
    Process(s"rm -rf $dir") !

  def nsToPath(ns: String): String =
    ns.replaceAll("\\.", "/")

  def stripSuffix(filename: String): String =
    filename.split("\\.").head

  def makeOutputPath(grammarFile: File, outputDir: File, namespace: String): String =
    s"$outputDir/${nsToPath(namespace)}/${stripSuffix(grammarFile.getName)}"

  def bnfcGenerateSources(fullClasspath: Seq[Attributed[File]], grammarFile: File, outputDir: File, namespace: String): Unit = {
    val classpath: String = fullClasspath.map(e => e.data).mkString(":")
    val targPath: String  = makeOutputPath(grammarFile, outputDir, namespace)
    val bnfcCmd: String   = s"bnfc --java -o ${outputDir.getAbsolutePath} -p $namespace $grammarFile"
    val jlexCmd: String   = s"java -cp $classpath JLex.Main $targPath/Yylex"
    val cupCmd: String    = s"java -cp $classpath java_cup.Main -nopositions -expect 100 $targPath/${stripSuffix(grammarFile.getName)}.cup"
    val mvCmd: String     = s"mv sym.java parser.java $targPath"
    Process(bnfcCmd) #&& Process(jlexCmd) #&& Process(cupCmd) #&& Process(mvCmd) !
  }

  def bnfcGenerateLaTeX(grammarFile: File, outputDir: File): Unit = {
    val bnfcCmd: String = s"bnfc --latex -o ${outputDir.getAbsolutePath} $grammarFile"
    Process(bnfcCmd) !
  }

  def bnfcFiles(base: File): Seq[File] = (base * "*.cf").get

  lazy val bnfcSettings = inConfig(BNFCConfig)(Defaults.configSettings ++ Seq(
    javaSource     := (javaSource in Compile).value,
    scalaSource    := (javaSource in Compile).value,
    bnfcNamespace  := "rholang.parsing",
    bnfcGrammarDir := baseDirectory.value / "src" / "main" / "bnfc",
    bnfcOutputDir  := (javaSource in Compile).value,
    bnfcDocDir     := baseDirectory.value / "doc" / "bnfc",
    clean          := cleanDir(bnfcOutputDir.value / nsToPath(bnfcNamespace.value)),
    generate       := bnfcFiles(bnfcGrammarDir.value).foreach { (f: File) =>
      bnfcGenerateSources((fullClasspath in BNFCConfig).value, f, bnfcOutputDir.value, bnfcNamespace.value)
    },
    cleanDocs      := cleanDir(bnfcDocDir.value),
    generateDocs   := bnfcFiles(bnfcGrammarDir.value).foreach { (f: File) =>
      bnfcGenerateLaTeX(f, bnfcDocDir.value)
    }))
}
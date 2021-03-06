/*
 * Copyright 2014-2017 Rik van der Kleij
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package intellij.haskell.external.execution

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import intellij.haskell.HaskellNotificationGroup
import intellij.haskell.util.{HaskellFileUtil, StringUtil}

import scala.collection.JavaConverters._

object HaskellCompilationResultHelper {

  private final val ProblemPattern = """(.+):([\d]+):([\d]+):(.+)""".r

  def createCompilationResult(currentPsiFile: Option[PsiFile], errorLines: Seq[String], failed: Boolean): CompilationResult = {
    val filePath = currentPsiFile.map(HaskellFileUtil.getAbsoluteFilePath)

    // `distinct` because of https://github.com/commercialhaskell/intero/issues/258
    val compilationProblems = errorLines.distinct.flatMap(l => parseErrorLine(filePath, l))

    val currentFileProblems = compilationProblems.flatMap(convertToCompilationProblemInCurrentFile)
    val otherFileProblems = compilationProblems.diff(currentFileProblems)

    CompilationResult(currentFileProblems, otherFileProblems, failed)
  }


  def createNotificationsForErrorsNotInCurrentFile(project: Project, compilationResult: CompilationResult): Unit = {
    if (compilationResult.currentFileProblems.isEmpty) {
      compilationResult.otherFileProblems.foreach {
        case cpf: CompilationProblemInOtherFile if !cpf.isWarning => HaskellNotificationGroup.logErrorBalloonEventWithLink(project, cpf.filePath, cpf.htmlMessage, cpf.lineNr, cpf.columnNr)
        case _ => ()
      }
    }
  }

  def showBuildErrors(project: Project, currentPsiFile: Option[PsiFile], processOutput: ProcessOutput): Unit = {
    val stderrLines = StringUtil.joinIndentedLines(project, processOutput.getStderrLines.asScala)
    val result = createCompilationResult(currentPsiFile, stderrLines, processOutput.getExitCode > 0)
    createNotificationsForErrorsNotInCurrentFile(project, result)
  }

  private def convertToCompilationProblemInCurrentFile(problem: CompilationProblem) = {
    problem match {
      case p: CompilationProblemInCurrentFile => Some(p)
      case _ => None
    }
  }

  def parseErrorLine(filePath: Option[String], errorLine: String): Option[CompilationProblem] = {
    errorLine match {
      case ProblemPattern(problemFilePath, lineNr, columnNr, message) =>
        val displayMessage = message.trim.replaceAll("""(\s\s\s\s+)""", "\n" + "$1")
        if (filePath.contains(problemFilePath)) {
          Some(CompilationProblemInCurrentFile(problemFilePath, lineNr.toInt, columnNr.toInt, displayMessage))
        } else {
          Some(CompilationProblemInOtherFile(problemFilePath, lineNr.toInt, columnNr.toInt, displayMessage))
        }
      case _ => None
    }
  }
}

case class CompilationResult(currentFileProblems: Iterable[CompilationProblemInCurrentFile], otherFileProblems: Iterable[CompilationProblem], failed: Boolean)

trait CompilationProblem {

  def message: String

  def plainMessage: String = {
    message.split("\n").mkString.replaceAll("\\s+", " ")
  }

  def htmlMessage: String = {
    StringUtil.escapeString(message.replace(' ', '\u00A0'))
  }

  def isWarning: Boolean = {
    message.startsWith("warning:") || message.startsWith("Warning:")
  }
}

case class CompilationProblemInCurrentFile private(filePath: String, lineNr: Int, columnNr: Int, message: String) extends CompilationProblem

case class CompilationProblemInOtherFile private(filePath: String, lineNr: Int, columnNr: Int, message: String) extends CompilationProblem


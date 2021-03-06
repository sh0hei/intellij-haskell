package intellij.haskell.runconfig.console

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.{CommandLineState, GeneralCommandLine}
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.process.{ProcessHandler, ProcessTerminatedListener}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import intellij.haskell.sdk.HaskellSdkType
import intellij.haskell.util.{GhcVersion, HaskellProjectUtil}

class HaskellConsoleState(val configuration: HaskellConsoleConfiguration, val environment: ExecutionEnvironment) extends CommandLineState(environment) {

  val consoleBuilder = new TextConsoleBuilderImpl(configuration.getProject) {
    override def getConsole: ConsoleView = {
      new HaskellConsoleView(configuration.getProject, configuration)
    }
  }
  setConsoleBuilder(consoleBuilder)

  protected def startProcess: ProcessHandler = {
    val project = configuration.getProject

    HaskellSdkType.getStackPath(project) match {
      case Some(stackPath) =>
        val stackTarget = configuration.getStackTarget
        val commandLine = new GeneralCommandLine(stackPath)
          .withParameters(configuration.replCommand)
          .withWorkDirectory(project.getBasePath)

        if (stackTarget.nonEmpty) {
          commandLine.addParameter(stackTarget)
        }

        // Enable color output for GHC versions that support it.
        HaskellProjectUtil.getGhcVersion(project).foreach { ghcVersion =>
          if (ghcVersion >= GhcVersion(8, 2, 1)) {
            commandLine.addParameters("--ghc-options", "-fdiagnostics-color=always")
          }
        }

        commandLine.setRedirectErrorStream(true)

        val handler = new HaskellConsoleProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString(), consoleBuilder.getConsole.asInstanceOf[HaskellConsoleView])
        ProcessTerminatedListener.attach(handler)
        handler
      case None => throw new CantRunException("Invalid Haskell Stack SDK")
    }
  }
}

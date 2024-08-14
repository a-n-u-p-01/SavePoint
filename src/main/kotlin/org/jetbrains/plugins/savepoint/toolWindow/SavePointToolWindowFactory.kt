package org.jetbrains.plugins.savepoint.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.plugins.savepoint.services.SavePointService
import java.awt.Color
import java.awt.FlowLayout
import java.io.File
import java.io.FileNotFoundException
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class SavePointToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = SavePointToolWindow(project).getContent()
        val contentFactory = ContentFactory.getInstance()
        val contentBuilder = contentFactory.createContent(content, null, false)
        toolWindow.contentManager.addContent(contentBuilder)
    }

    class SavePointToolWindow(private val project: Project) {

        private val savePointService = project.service<SavePointService>()
        private val savePointDir = File(project.basePath, ".savepoints")

        fun getContent(): JPanel {
            return JPanel().apply {
                layout = FlowLayout(FlowLayout.CENTER)

                add(JButton("Add Save Point").apply {
                    addActionListener {
                        try {
                            val name = Messages.showInputDialog(
                                project,
                                "Enter save point name:",
                                "Add Save Point",
                                Messages.getQuestionIcon()
                            ) ?: return@addActionListener
                            if (name.isBlank()) {
                                Messages.showErrorDialog(project, "Save point name cannot be empty.", "Error")
                                return@addActionListener
                            }

                            val message = Messages.showInputDialog(
                                project,
                                "Enter save point message:",
                                "Add Save Point",
                                Messages.getQuestionIcon()
                            ) ?: return@addActionListener
                            if (message.isBlank()) {
                                Messages.showErrorDialog(project, "Save point message cannot be empty.", "Error")
                                return@addActionListener
                            }

                            if(savePointService.addSavePoint(name, message)){
                                showSuccessMessage("Save point '$name' added successfully.")
                            }

                        } catch (e: Exception) {
                            Messages.showErrorDialog("An error occurred while adding the save point: ${e.message}", "Error")
                        }
                    }
                })

                add(JButton("Rollback").apply {
                    addActionListener {
                        try {
                            val savePoints = savePointService.getSavePoints()
                            val savePointNames = savePoints.map { "${it.first} - ${it.second.substringBefore('\n')}" }.toTypedArray()
                            val selectedName = Messages.showEditableChooseDialog(
                                "Select a save point to rollback to:",
                                "Rollback",
                                Messages.getQuestionIcon(),
                                savePointNames,
                                savePointNames.firstOrNull(),
                                null
                            ) ?: return@addActionListener

                            val selectedSavePoint = savePoints.find { "${it.first} - ${it.second.substringBefore('\n')}" == selectedName }
                                ?: run {
                                    Messages.showErrorDialog("Selected save point was not found.", "Error")
                                    return@addActionListener
                                }

                            val confirm = Messages.showOkCancelDialog(
                                project,
                                "Are you sure you want to roll back to the save point '$selectedName'?",
                                "Confirm Rollback",
                                Messages.getWarningIcon()
                            )

                            if (confirm == Messages.OK) {
                                savePointService.rollbackToSavePoint(selectedSavePoint.first)
                                showSuccessMessage("Rollback to save point '$selectedName' was successful.")
                            }
                        } catch (e: Exception) {
                            Messages.showErrorDialog("An error occurred while rolling back: ${e.message}", "Error")
                        }
                    }
                })

                add(JButton("Undo Rollback").apply {
                    addActionListener {
                        val confirm = Messages.showOkCancelDialog(
                            project,
                            "Are you sure you want to undo the rollback?",
                            "Confirm Undo Rollback",
                            Messages.getWarningIcon()
                        )

                        if (confirm == Messages.OK) {
                            try {
                                if(savePointService.undoRollback()){
                                    showSuccessMessage("Undo rollback was successful.")
                                }
                            } catch (e: Exception) {
                                Messages.showErrorDialog("An error occurred while undoing the rollback: ${e.message}", "Error")
                            }
                        }
                    }
                })

                add(JButton("Save Points").apply {
                    addActionListener {
                        try {
                            val savePoints = savePointService.getSavePoints()
                            val savePointNames = savePoints.map { "${it.first} - ${it.second.substringBefore('\n')}" }.toTypedArray()
                            val selectedName = Messages.showEditableChooseDialog(
                                "Select a save point:",
                                "Save Points",
                                Messages.getQuestionIcon(),
                                savePointNames,
                                savePointNames.firstOrNull(),
                                null
                            ) ?: return@addActionListener

                            val selectedSavePoint = savePoints.find { "${it.first} - ${it.second.substringBefore('\n')}" == selectedName }
                            selectedSavePoint?.let {
                                Messages.showInfoMessage(project, "Message for '${it.first}':\n${it.second}", "Save Point Message")
                            } ?: run {
                                Messages.showErrorDialog("Selected save point was not found.", "Error")
                            }
                        } catch (e: Exception) {
                            Messages.showErrorDialog("An error occurred while fetching save points: ${e.message}", "Error")
                        }
                    }
                })

                add(JButton("Delete").apply {
                    addActionListener {
                        try {
                            val savePoints = savePointService.getSavePoints()
                            val savePointNames = savePoints.map { "${it.first} - ${it.second.substringBefore('\n')}" }.toTypedArray()

                            val selectedName = Messages.showEditableChooseDialog(
                                "Select a save point to delete:",
                                "Delete Save Point",
                                Messages.getQuestionIcon(),
                                savePointNames,
                                savePointNames.firstOrNull(),
                                null
                            ) ?: return@addActionListener

                            val selectedSavePoint = savePoints.find { "${it.first} - ${it.second.substringBefore('\n')}" == selectedName }
                            if (selectedSavePoint != null) {
                                val name = selectedSavePoint.first
                                val zipFile = File(savePointDir, "$name.zip")
                                val txtFile = File(savePointDir, "$name.txt")

                                val confirmation = Messages.showOkCancelDialog(
                                    project,
                                    "Are you sure you want to delete the save point '$name'?",
                                    "Confirm Deletion",
                                    "Yes",
                                    "No",
                                    Messages.getWarningIcon()
                                )

                                if (confirmation == Messages.OK) {
                                    if (zipFile.exists() && zipFile.delete()) {
                                        showSuccessMessage("Save point '$name' deleted successfully.")
                                    } else {
                                        Messages.showErrorDialog("Failed to delete save point '$name'. Zip file might be missing or could not be deleted.", "Error")
                                    }

                                    if (txtFile.exists() && txtFile.delete()) {
                                        showSuccessMessage("Save point '$name' TXT file deleted successfully.")
                                    } else {
                                        if (txtFile.exists()) {
                                            Messages.showErrorDialog("Failed to delete save point '$name' TXT file.", "Error")
                                        }
                                    }
                                }
                            } else {
                                Messages.showErrorDialog("Selected save point was not found.", "Error")
                            }
                        } catch (e: FileNotFoundException) {
                            Messages.showErrorDialog("An error occurred while accessing the files: ${e.message}", "Error")
                        } catch (e: Exception) {
                            Messages.showErrorDialog("An unexpected error occurred: ${e.message}", "Error")
                        }
                    }
                })

                add(JButton("Refresh").apply {
                    toolTipText = "Refresh"
                    addActionListener {
                        try {
                            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
                            showSuccessMessage("IDE refreshed successfully.")
                        } catch (e: Exception) {
                            Messages.showErrorDialog("An error occurred while refreshing: ${e.message}", "Error")
                        }
                    }
                })
            }
        }

        private fun showSuccessMessage(message: String) {
            Messages.showMessageDialog(
                project,
                message,
                "Success",
                AllIcons.General.SuccessDialog // Using IntelliJ's general information icon
            )
        }
    }
}

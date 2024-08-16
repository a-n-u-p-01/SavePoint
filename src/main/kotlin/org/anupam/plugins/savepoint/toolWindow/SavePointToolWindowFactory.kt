package org.anupam.plugins.savepoint.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.anupam.plugins.savepoint.services.SavePointService
import java.awt.Dimension
import java.io.File
import java.io.FileNotFoundException
import javax.swing.*

class SavePointToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val icon = IconLoader.getIcon("META-INF/pluginIcon.png", this.javaClass)
        toolWindow.setIcon(icon)

        val content = SavePointToolWindow(project).getContent()
        val contentFactory = ContentFactory.getInstance()
        val contentBuilder = contentFactory.createContent(content, null, false)
        toolWindow.contentManager.addContent(contentBuilder)

        // Ensure the tool window is docked on the right
        toolWindow.setAnchor(ToolWindowAnchor.RIGHT, null)
        toolWindow.isAvailable = true
        toolWindow.isShowStripeButton = true

        // Force the tool window to update its size
        SwingUtilities.invokeLater {
            toolWindow.component.preferredSize = Dimension(300, 200) // Adjust width and height as needed
            toolWindow.component.revalidate()
            toolWindow.component.repaint()
        }
    }

    class SavePointToolWindow(private val project: Project) {

        private val savePointService = project.service<SavePointService>()

        fun getSavePointsDir(): File {
            val savePointsDirPath = File(System.getProperty("user.home"), "SavePointData")
            if (!savePointsDirPath.exists()) {
                savePointsDirPath.mkdirs()
            }
            return savePointsDirPath
        }

        fun getContent(): JPanel {
            return JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                layout = BoxLayout(this,BoxLayout.PAGE_AXIS)

                add(JButton("Save Points          ").apply {
                    icon = AllIcons.Actions.ShowAsTree
                    addActionListener {
                        try {
                            val savePoints = savePointService.getSavePoints()
                            if(savePoints.isEmpty()){
                                Messages.showInfoMessage("Do not have any save point", "Information")
                                return@addActionListener
                            }
                            val savePointNames = savePoints.map { "${it.first} - ${it.second.substringBefore('\n')}" }.toTypedArray()
                            val selectedName = Messages.showEditableChooseDialog(
                                "Select a save point to get Information",
                                "Show Save Point",
                                Messages.getQuestionIcon(),
                                savePointNames,
                                savePointNames.firstOrNull(),
                                null
                            )?.trim() ?: return@addActionListener

                            val selectedSavePoint = savePoints.find { "${it.first} - ${it.second.substringBefore('\n')}" == selectedName }
                            selectedSavePoint?.let {
                                Messages.showInfoMessage(project, "${it.first}\n Comment: ${it.second}", "Save Point Info")
                            } ?: run {
                                Messages.showErrorDialog("Selected save point was not found.", "Error")
                            }
                        } catch (e: Exception) {
                            Messages.showErrorDialog("An error occurred while fetching save points: ${e.message}", "Error")
                        }
                    }
                })


                add(JButton("Add Save Point    ").apply {
                    icon = AllIcons.Actions.Commit
                    addActionListener {
                        try {
                            val name = Messages.showInputDialog(
                                project,
                                "Enter save point name:",
                                "Add Save Point",
                                Messages.getQuestionIcon()
                            )?.trim() ?: return@addActionListener
                            if (name.isBlank()) {
                                Messages.showErrorDialog(project, "Save point name cannot be empty.", "Error")
                                return@addActionListener
                            }

                            val message = Messages.showInputDialog(
                                project,
                                "Enter save point message:",
                                "Add Save Point",
                                Messages.getQuestionIcon()
                            )?.trim() ?: return@addActionListener
                            if (message.isBlank()) {
                                Messages.showErrorDialog(project, "Save point message cannot be empty.", "Error")
                                return@addActionListener
                            }

                            if (savePointService.addSavePoint(name, message)) {
                                showSuccessMessage("Save point '$name' added successfully.")
                            } else {
                                Messages.showErrorDialog("Failed to add save point.", "Error")
                            }

                        } catch (e: Exception) {
                            Messages.showErrorDialog("An error occurred while adding the save point: ${e.message}", "Error")
                        }
                    }
                })

                add(JButton("Delete                 ").apply {
                    icon = AllIcons.Actions.RemoveMulticaret
                    addActionListener {
                        try {
                            val savePoints = savePointService.getSavePoints()
                            if(savePoints.isEmpty()){
                                Messages.showInfoMessage("Do not have any save point", "Information")
                                return@addActionListener
                            }

                            val savePointNames = savePoints.map { "${it.first} - ${it.second.substringBefore('\n')}" }.toTypedArray()
                            val selectedName = Messages.showEditableChooseDialog(
                                "Select a save point for delete",
                                "Delete Save Point",
                                Messages.getQuestionIcon(),
                                savePointNames,
                                savePointNames.firstOrNull(), // Default value if the list is not empty
                                null
                            )?.trim() ?: run {
                                return@addActionListener
                            }


                            val selectedSavePoint = savePoints.find { "${it.first} - ${it.second.substringBefore('\n')}" == selectedName }
                            if (selectedSavePoint != null) {
                                val name = selectedSavePoint.first
                                val confirmation = Messages.showOkCancelDialog(
                                    project,
                                    "Are you sure you want to delete the save point '$name'?",
                                    "Confirm Deletion",
                                    "Yes",
                                    "No",
                                    Messages.getWarningIcon()
                                )

                                if (confirmation == Messages.OK) {
                                    if (savePointService.deleteSavePoint(name)) {
                                        showSuccessMessage("Save point '$name' deleted successfully.")
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


                add(JButton("Rollback               ").apply {
                    icon =  AllIcons.Actions.Rollback
                    addActionListener {
                        try {
                            val savePoints = savePointService.getSavePoints()
                            if(savePoints.isEmpty()){
                                Messages.showInfoMessage("Do not have any save point", "Information")
                                return@addActionListener
                            }
                            val savePointNames = savePoints.map { "${it.first} - ${it.second.substringBefore('\n')}" }.toTypedArray()
                            val selectedName = Messages.showEditableChooseDialog(
                                "Select a save point to RollBack",
                                "Rollback",
                                Messages.getQuestionIcon(),
                                savePointNames,
                                savePointNames.firstOrNull(),
                                null
                            )?.trim() ?: return@addActionListener

                            val selectedSavePoint = savePoints.find { "${it.first} - ${it.second.substringBefore('\n')}" == selectedName }
                                ?: run {
                                    Messages.showErrorDialog("Selected save point was not found.", "Error")
                                    return@addActionListener
                                }
                            val confirm =   Messages.showOkCancelDialog(
                                project,
                                "Are you sure you want to roll back to the save point '$selectedName'?",
                                "Confirm Rollback",
                                Messages.getOkButton(),
                                Messages.getCancelButton(),
                                Messages.getQuestionIcon()
                            )
                            if (confirm == Messages.OK) {
                                savePointService.rollbackToSavePoint(selectedSavePoint.first)
                                VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
                                showSuccessMessage("Rollback to save point '$selectedName' was successful.")
                            }
                        } catch (e: Exception) {
                            Messages.showErrorDialog("An error occurred while rolling back: ${e.message}", "Error")
                        }
                    }
                })

                add(JButton("Undo Rollback     ").apply {
                    icon = AllIcons.Actions.Undo
                    addActionListener {
                        val saveProjectName = savePointService.replaceBackslashes(savePointService.getProjectRoot().toString())
                        val eachI = File(getSavePointsDir(),saveProjectName)
                        val preRollBackFile = File(eachI, "preRollback")
                        val confirm: Int
                        if (preRollBackFile.exists()) {
                            confirm = Messages.showOkCancelDialog(
                                project,
                                "Are you sure you want to undo the rollback?",
                                "Confirm Undo Rollback",
                                Messages.getOkButton(),
                                Messages.getCancelButton(),
                                Messages.getQuestionIcon()
                            )
                        }else{
                            Messages.showInfoMessage("Have not done any recent RollBack to Undo.", "Not Available")
                            return@addActionListener
                        }

                        if (confirm == Messages.OK) {
                            try {
                                if (savePointService.undoRollback()) {
                                    showSuccessMessage("Undo rollback was successful.")
                                    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
                                } else {
                                    Messages.showErrorDialog("Undo rollback failed.", "Error")
                                }
                            } catch (e: Exception) {
                                Messages.showErrorDialog("An error occurred while undoing the rollback: ${e.message}", "Error")
                            }
                        }
                    }
                })



                add(JButton("Backup                ").apply {
                    icon = AllIcons.Actions.Upload
                    addActionListener {
                        val confirmation = Messages.showOkCancelDialog(
                            project,
                            "Are you sure you want to Backup the project'${project.name}'?",
                            "Confirm Backup",
                            "Yes",
                            "No",
                            Messages.getQuestionIcon()
                        )
                        if (confirmation == Messages.OK) {
                            savePointService.backupProject()
                            showSuccessMessage("Project backup successfully.\n Time : ${savePointService.getTimestamp()}")
                        }
                    }
                })


                add(JButton("Restore                ").apply {
                    icon = AllIcons.Actions.Install
                    addActionListener {

                      val lastBackupTime = File(savePointService.getProjectBackupDir(), "message.txt").readText()
                        val backupDir = File(savePointService.getProjectBackupDir(),"backup")

                        if(backupDir.exists()) {
                            val confirmation = Messages.showOkCancelDialog(
                                project,
                                "Are you sure you want to restore project '$name' backup? \n Last Time Backed up At : $lastBackupTime",
                                "Confirm Restore",
                                "Yes",
                                "No",
                                Messages.getQuestionIcon(),
                            )
                            if (confirmation == Messages.OK){
                                try {
                                    if(savePointService.restore()){
                                        VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
                                        showSuccessMessage(" ${project.name} -> Restored successfully.")
                                    }
                                } catch (e: Exception) {
                                    Messages.showErrorDialog(" : ${project.name}", "Operation Failed")
                                }
                            }
                        }
                        else{
                            Messages.showInfoMessage("No backup found for project: ${project.name}", "Backup Missing")
                        }
                    }
                })


                add(JButton("Path                     ").apply {
                    icon = AllIcons.Actions.Preview
                    addActionListener {
                        // Retrieve the file paths
                        val saveProjectName = savePointService.replaceBackslashes(savePointService.getProjectRoot().toString())
                        val (path1, path2) = savePointService.getBackUpFilesAddress()

                        // Display the file paths in a popup window with each path on a separate line
                        Messages.showInfoMessage("1.Saved Points Path:\n '$path1' \n\n2.Backup Project File Directory:\n '$path2'\n\n Saved Name of Project : '$saveProjectName'","Info")
                    }
                })

                add(JButton("Refresh                ").apply {
                    toolTipText = "Refresh"
                    icon = AllIcons.Actions.BuildLoadChanges
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

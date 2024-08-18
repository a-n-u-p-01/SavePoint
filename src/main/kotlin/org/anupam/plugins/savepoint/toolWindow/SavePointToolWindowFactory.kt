package org.anupam.plugins.savepoint.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
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
        val icon = IconLoader.getIcon("META-INF/icons/pluginIcon.png", this.javaClass)
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

                add(JButton("Add                      ").apply {
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
                            }
                        } catch (e: Exception) {
                            Messages.showErrorDialog("An error occurred while adding the save point: ${e.message}", "Error")
                        }
                    }
                })

                add(JButton("Remove                ").apply {
                    icon = AllIcons.Actions.RemoveMulticaret
                    addActionListener {
                        try {
                            val savePoints = savePointService.getSavePoints()

                            if (savePoints.isEmpty()) {
                                Messages.showInfoMessage("No save points available.", "Information")
                                return@addActionListener
                            }

                            // Sort save points by time in descending order (latest first)
                            val sortedSavePoints = savePoints.sortedByDescending { it.second }

                            // Create display names without numbering
                            val savePointNames = sortedSavePoints
                                .map { "${it.first} - ${it.second.substringBefore('\n')}" }
                                .toTypedArray()

                            // Show the editable choose dialog
                            val selectedName = Messages.showEditableChooseDialog(
                                "Select a save point to remove",
                                "Remove Save Point",
                                Messages.getQuestionIcon(),
                                savePointNames,
                                savePointNames.firstOrNull(), // Default value if the list is not empty
                                null
                            )?.trim() ?: return@addActionListener

                            // Find the index of the selected name in the displayed names
                            val selectedIndex = savePointNames.indexOfFirst { it.startsWith(selectedName) }

                            // Ensure we have a valid index and select the corresponding save point
                            if (selectedIndex != -1) {
                                val selectedSavePoint = sortedSavePoints[selectedIndex]

                                val name = selectedSavePoint.first
                                val confirmation = Messages.showOkCancelDialog(
                                    project,
                                    "Are you sure you want to remove the save point '$name'?",
                                    "Confirm Removal",
                                    "Yes",
                                    "No",
                                    Messages.getWarningIcon()
                                )

                                if (confirmation == Messages.OK) {
                                    if (savePointService.deleteSavePoint(name)) {
                                        showSuccessMessage("Save point '$name' removed successfully.")
                                    } else {
                                        Messages.showErrorDialog("Failed to remove the save point '$name'.", "Error")
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

                add(JButton("Save Points          ").apply {
                    icon = AllIcons.Actions.ShowAsTree
                    addActionListener {
                        try {
                            val savePoints = savePointService.getSavePoints()

                            if (savePoints.isEmpty()) {
                                Messages.showInfoMessage("No save points available.", "Information")
                                return@addActionListener
                            }

                            // Sort save points by time in descending order (latest first)
                            val sortedSavePoints = savePoints.sortedByDescending { it.second }

                            // Create display names without numbering
                            val savePointNames = sortedSavePoints
                                .map { "${it.first} - ${it.second.substringBefore('\n')}" }
                                .toTypedArray()

                            // Show the editable choose dialog
                            val selectedName = Messages.showEditableChooseDialog(
                                "Select a save point to view details",
                                "Show Save Point",
                                Messages.getQuestionIcon(),
                                savePointNames,
                                savePointNames.firstOrNull(),
                                null
                            )?.trim() ?: return@addActionListener

                            // Find the index of the selected name in the displayed names
                            val selectedIndex = savePointNames.indexOfFirst { it.startsWith(selectedName) }

                            // Ensure we have a valid index and select the corresponding save point
                            if (selectedIndex != -1) {
                                val selectedSavePoint = sortedSavePoints[selectedIndex]

                                // Show information about the selected save point
                                Messages.showInfoMessage(
                                    project,
                                    "${selectedSavePoint.first}\nComment: ${selectedSavePoint.second}",
                                    "Save Point Info"
                                )
                            } else {
                                Messages.showErrorDialog("Selected save point was not found.", "Error")
                            }
                        } catch (e: Exception) {
                            Messages.showErrorDialog(
                                "An error occurred while fetching save points: ${e.message}",
                                "Error"
                            )
                        }
                    }
                })

                add(JButton("Rollback               ").apply {
                    icon = AllIcons.Actions.Rollback
                    addActionListener {
                        try {
                            val savePoints = savePointService.getSavePoints()

                            if (savePoints.isEmpty()) {
                                Messages.showInfoMessage("No save points available.", "Information")
                                return@addActionListener
                            }

                            // Sort save points by time in descending order (latest first)
                            val sortedSavePoints = savePoints.sortedByDescending { it.second }

                            // Create display names without numbering
                            val savePointNames = sortedSavePoints
                                .map { "${it.first} - ${it.second.substringBefore('\n')}" }
                                .toTypedArray()

                            // Show the editable choose dialog
                            val selectedName = Messages.showEditableChooseDialog(
                                "Select a save point to Rollback",
                                "Rollback",
                                Messages.getQuestionIcon(),
                                savePointNames,
                                savePointNames.firstOrNull(), // Default value if the list is not empty
                                null
                            )?.trim() ?: return@addActionListener

                            // Find the index of the selected name in the displayed names
                            val selectedIndex = savePointNames.indexOfFirst { it.startsWith(selectedName) }

                            // Ensure we have a valid index and select the corresponding save point
                            if (selectedIndex != -1) {
                                val selectedSavePoint = sortedSavePoints[selectedIndex]

                                val name = selectedSavePoint.first
                                val confirm = Messages.showOkCancelDialog(
                                    project,
                                    "Are you sure you want to roll back to the save point '$name'? This will revert to the selected save point and discard the current changes. You can undo this rollback, but only once, and multiple rollbacks will only allow undoing the most recent one.",
                                    "Confirm Rollback",
                                    Messages.getOkButton(),
                                    Messages.getCancelButton(),
                                    Messages.getQuestionIcon()
                                )

                                if (confirm == Messages.OK) {
                                    savePointService.rollbackToSavePoint(name)
                                    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
                                    showSuccessMessage("Rollback to save point '$name' was successful. If changes does not reflect then please restart the IDE.")
                                }
                            } else {
                                Messages.showErrorDialog("Selected save point was not found.", "Error")
                            }
                        } catch (e: Exception) {
                            Messages.showErrorDialog("An error occurred while rolling back: ${e.message}", "Error")
                        }
                    }
                })


                add(JButton("Undo Rollback      ").apply {
                    icon = AllIcons.Actions.Undo
                    addActionListener {
                        val saveProjectName = savePointService.sanitizeFolderName(savePointService.getProjectRoot().toString())
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
                                    showSuccessMessage("Undo rollback was successful. If changes does not reflect then please restart the IDE")
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



                add(JButton("Commit                 ").apply {
                    icon = AllIcons.Actions.Upload
                    addActionListener {
                        val confirmation = Messages.showOkCancelDialog(
                            project,
                            "Are you sure you want to commit the project '${project.name}'? This action will permanently save all your recent changes and updates. Review your changes carefully before proceeding, as this action cannot be undone",
                            "Confirm Backup",
                            "Yes",
                            "No",
                            Messages.getQuestionIcon()
                        )
                        if (confirmation == Messages.OK) {
                            NotificationGroupManager.getInstance().getNotificationGroup("Custom Notifications")
                                .createNotification("Refresh completed successfully.", NotificationType.INFORMATION)
                                .notify(null)
                            savePointService.backupProject()
                            showSuccessMessage("${project.name} Commited successfully.\n Time : ${savePointService.getTimestamp()}")
                        }
                    }
                })

                add(JButton("Path                      ").apply {
                    icon = AllIcons.Actions.Preview
                    addActionListener {
                        // Retrieve the file paths
                        val saveProjectName = savePointService.sanitizeFolderName(savePointService.getProjectRoot().toString())
                        val (path1, path2) = savePointService.getBackUpFilesAddress()

                        // Display the file paths in a popup window with each path on a separate line
                        Messages.showInfoMessage("1. Saved Name of Project : [$saveProjectName]\n\n2. Saved Points Path:\n [$path1] \n\n3.Final Project File Directory:\n [$path2]","Info")
                    }
                })

                add(JButton("Refresh                 ").apply {
                    toolTipText = "Refresh"
                    icon = AllIcons.Actions.BuildLoadChanges
                    addActionListener {
                        try {
                            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)

                            // Create the notification
                            val notification = NotificationGroupManager.getInstance()
                                .getNotificationGroup("Custom Notifications")
                                .createNotification("Refresh completed successfully.", NotificationType.INFORMATION)

                            // Show the notification
                            notification.notify(null)

                            // Set a timer to expire the notification after 2 seconds
                            Timer(1000) {
                                notification.expire()  // Hides the notification after 2 seconds
                            }.start()

                        } catch (e: Exception) {
                            // Show error message if an exception occurs
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

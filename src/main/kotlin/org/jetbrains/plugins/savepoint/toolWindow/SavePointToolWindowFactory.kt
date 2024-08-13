package org.jetbrains.plugins.savepoint.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.jetbrains.plugins.savepoint.services.SavePointService
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JOptionPane

class SavePointToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = SavePointToolWindow(project).getContent()
        val contentFactory = ContentFactory.getInstance()
        val contentBuilder = contentFactory.createContent(content, null, false)
        toolWindow.contentManager.addContent(contentBuilder)
    }

    class SavePointToolWindow(private val project: Project) {

        private val savePointService = project.service<SavePointService>()

        fun getContent(): JPanel {
            return JPanel().apply {
                add(JLabel("Save Point Tool Window"))

                add(JButton("Add Save Point").apply {
                    addActionListener {
                        val message = JOptionPane.showInputDialog("Enter save point message:")
                        if (message != null && message.isNotBlank()) {
                            savePointService.addSavePoint(message)
                        }
                    }
                })

               add( JButton("Rollback to Save Point").apply {
                    addActionListener {
                        val savePointName = JOptionPane.showInputDialog("Enter the save point name to rollback to:")

                        if (savePointName.isNullOrBlank()) {
                            JOptionPane.showMessageDialog(this, "Save point name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE)
                            return@addActionListener
                        }
                        savePointService.rollbackToSavePoint(savePointName)
                    }
                })


                add(JButton("Show Save Points").apply {
                    addActionListener {
                        savePointService.showSavePoints()
                    }
                })
            }
        }
    }
}

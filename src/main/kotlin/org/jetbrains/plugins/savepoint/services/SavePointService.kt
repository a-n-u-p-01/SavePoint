package org.jetbrains.plugins.savepoint.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.plugins.savepoint.utils.AdvancedFileCopier
import java.io.*
import java.nio.file.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.logging.Logger
import java.util.zip.*
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

@Service(Service.Level.PROJECT)
class SavePointService(private val project: Project) {

    private val savePointDir: File = File(project.basePath, ".savepoints")
    private val logger: Logger = Logger.getLogger(SavePointService::class.java.name)
    private val fileCopier = AdvancedFileCopier(logger)
    private val executor = Executors.newSingleThreadExecutor()

    init {
        if (!savePointDir.exists()) {
            savePointDir.mkdirs()
        }
    }

    private fun getProjectRoot(): File? = project.basePath?.let { File(it) }

    private fun getTimestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    private fun getProjectBackupDir(): File {
        val backupDirPath = File(System.getProperty("user.home"), "ProjectBackups/${project.name}")
        if (!backupDirPath.exists()) {
            backupDirPath.mkdirs()
        }
        return backupDirPath
    }

    fun addSavePoint(name: String, message: String): Boolean {
        Messages.showInfoMessage("Click ok then wait. It may take few seconds depend on file size..", "Back Up Current State")
        backupProject()
        Messages.showInfoMessage("Click ok then wait. It may take few seconds depend on file size..", "Adding Save Point")
        val rootDir = getProjectRoot() ?: return false
        val srcDir = File(rootDir, "src")

        if (!srcDir.exists()) {
            Messages.showErrorDialog("The 'src' directory does not exist.", "Error")
            return false
        }

        val savePointDir = File(rootDir, ".savepoints")
        if (!savePointDir.exists() && !savePointDir.mkdirs()) {
            Messages.showErrorDialog("Failed to create '.savepoint' directory.", "Error")
            return false
        }

        val timestamp = getTimestamp()
        val savePointFile = File(savePointDir, "$name.zip")
        val messageFile = File(savePointDir, "$name.txt")

        if (savePointFile.exists()) {
            Messages.showErrorDialog("Save point '$name' already exists.", "Error")
            return false
        }

        try {
            // Perform compression in a separate thread to avoid blocking the UI thread
            val future: Future<*> = executor.submit {
                    createZip(savePointFile, srcDir.toPath())
                messageFile.writeText("$timestamp\n$message")

            }
            future.get() // Wait for the task to complete
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to create save point: ${e.message}", "Error")
            return false
        } catch (e: InterruptedException) {
            Messages.showErrorDialog("Save point creation was interrupted.", "Error")
            return false
        } catch (e: ExecutionException) {
            Messages.showErrorDialog("Failed to create save point: ${e.message}", "Error")
            return false
        }
        return true

    }

    private fun createZip(zipFile: File, sourceDir: Path) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
            Files.walk(sourceDir).forEach { path ->
                val file = path.toFile()
                val entryName = sourceDir.relativize(path).toString().replace("\\", "/")
                if (file.isDirectory) {
                    zipOut.putNextEntry(ZipEntry("$entryName/"))
                } else {
                    zipOut.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { fis -> fis.copyTo(zipOut) }
                }
                zipOut.closeEntry()

            }
        }
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipIn ->
            generateSequence { zipIn.nextEntry }.forEach { entry ->
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile.mkdirs()
                    FileOutputStream(file).use { fos -> zipIn.copyTo(fos) }
                }
            }
        }
    }

    fun rollbackToSavePoint(savePointName: String) {
        Messages.showInfoMessage("Click ok then wait. It may take few seconds depend on file size..", "Roll Back")
        val savePointFile = File(savePointDir, "$savePointName.zip")
        if (!savePointFile.exists()) {
            Messages.showErrorDialog("Save point '$savePointName' not found.", "Error")
            return
        }

        val timestamp = getTimestamp()
        val rootDir = getProjectRoot() ?: return
        val targetDir = File(rootDir, "src")

        try {
            val preRollbackFile = File(savePointDir, "preRollback.zip")
            val preRollbackMessageFile = File(savePointDir, "preRollback.txt")

            // Backup current state before rollback
            val future: Future<*> = executor.submit {
                createZip(preRollbackFile, targetDir.toPath())
                preRollbackMessageFile.writeText("$timestamp $savePointName")
            }
            future.get() // Wait for the task to complete

            if (targetDir.exists()) {
                deleteFile(targetDir)
            }

            extractZip(savePointFile, targetDir)

            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to rollback to save point: ${e.message}", "Error")
        } catch (e: InterruptedException) {
            Messages.showErrorDialog("Rollback was interrupted.", "Error")
        } catch (e: ExecutionException) {
            Messages.showErrorDialog("Failed to rollback to save point: ${e.message}", "Error")
        }
    }

    fun undoRollback(): Boolean {

        val preRollbackFile = File(savePointDir, "preRollback.zip")
        if (!preRollbackFile.exists()) {
            Messages.showWarningDialog("No rollback to undo.", "Undo Rollback")
            return false
        }
        Messages.showInfoMessage("Click ok then wait. It may take few seconds depend on file size..", "Roll Back")
        val rootDir = getProjectRoot() ?: return false
        val targetDir = File(rootDir, "src")

        try {
            val future: Future<*> = executor.submit {
                if (targetDir.exists()) {
                    deleteFile(targetDir)
                }
                extractZip(preRollbackFile, targetDir)
            }
            future.get() // Wait for the task to complete

            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
//            Messages.showInfoMessage("Rollback undone.", "Undo Rollback")
            preRollbackFile.delete()
            File(savePointDir, "preRollback.txt").delete()
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to undo rollback: ${e.message}", "Error")
            return false
        } catch (e: InterruptedException) {
            Messages.showErrorDialog("Undo rollback was interrupted.", "Error")
            return false
        } catch (e: ExecutionException) {
            Messages.showErrorDialog("Failed to undo rollback: ${e.message}", "Error")
            return false
        }
        return true
    }

    private fun deleteFile(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteFile(it) }
        }
        file.delete()
    }

    fun getSavePoints(): List<Pair<String, String>> {
        return savePointDir.listFiles()
            ?.filter { it.extension == "zip" }
            ?.map { file ->
                val nameWithoutExt = file.nameWithoutExtension
                val messageFile = File(savePointDir, "$nameWithoutExt.txt")
                val timestamp = messageFile.readLines().firstOrNull() ?: "Unknown"
                val message = messageFile.readText().substringAfter('\n').takeIf { it.isNotBlank() } ?: ""
                Pair(nameWithoutExt, "$timestamp\n$message")
            }
            ?.sortedByDescending { it.second.substringBefore('\n') }
            ?: emptyList()
    }

    fun deleteSavePoint(name: String) {
        val savePointZipFile = File(savePointDir, "$name.zip")
        val savePointTxtFile = File(savePointDir, "$name.txt")

        if (!savePointZipFile.exists() && savePointZipFile.delete()) {
            Messages.showErrorDialog("Failed to delete save point '$name'.", "Error")
        }

        if (savePointTxtFile.exists()) {
            savePointTxtFile.delete()
        }
    }

    fun backupProject() {
        val rootDir = getProjectRoot() ?: run {
            Messages.showErrorDialog("Failed to get project root.", "Error")
            return
        }


        val timestamp = getTimestamp()
        val backupDir = getProjectBackupDir()
        val backupFile = File(backupDir, "backup")

        try {
            if (backupFile.exists()) {
                backupFile.deleteRecursively()
            }
            backupFile.mkdirs()

            // Perform backup in a separate thread to avoid blocking the UI thread
            val future: Future<*> = executor.submit {
                fileCopier.copyDirectory(rootDir.toPath(), backupFile.toPath())
            }
            future.get() // Wait for the task to complete
            val backupLocationMessage = "Project backed up successfully to '${backupFile.absolutePath}'."
            Messages.showInfoMessage(backupLocationMessage, "Backup Successful")
            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to backup project: ${e.message}", "Error")
        } catch (e: InterruptedException) {
            Messages.showErrorDialog("Backup was interrupted.", "Error")
        } catch (e: ExecutionException) {
            Messages.showErrorDialog("Failed to backup project: ${e.message}", "Error")
        }
    }
}

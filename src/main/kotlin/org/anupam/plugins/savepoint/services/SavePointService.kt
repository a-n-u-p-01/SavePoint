package org.anupam.plugins.savepoint.services
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFileManager
import org.anupam.plugins.savepoint.utils.AdvancedFileCopier
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.logging.Logger


@Service(Service.Level.PROJECT)
class SavePointService(private val project: Project) {
    private val savePointsDir =  getSavePointsDir()
    private val logger: Logger = Logger.getLogger(SavePointService::class.java.name)
    private val fileCopier = AdvancedFileCopier(logger)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        if (!savePointsDir.exists()) {
            savePointsDir.mkdirs()
        }
    }

    //Current project
    fun getProjectRoot(): File? = project.basePath?.let { File(it) }

    fun getTimestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss a", Locale.getDefault()).format(Date())

    // Back up User.Home ProjectBackup
    fun getProjectBackupDir(): File {
        val saveProjectName = sanitizeFolderName(getProjectRoot().toString())
        val backupDirPath = File(System.getProperty("user.home"), "ProjectBackups/${saveProjectName}")
        if (!backupDirPath.exists()) {
            backupDirPath.mkdirs()
        }
        return backupDirPath
    }
    //Saved Instances of projects
    private fun getSavePointsDir(): File {
        val savePointsDirPath = File(System.getProperty("user.home"), "SavePointData")
        if (!savePointsDirPath.exists()) {
            savePointsDirPath.mkdirs()
        }
        return savePointsDirPath
    }


    fun addSavePoint(name: String, message: String): Boolean {
        val timestamp = getTimestamp()
        val rootDir = getProjectRoot() ?: return false
        val saveProjectName = sanitizeFolderName(rootDir.toString())
        val eachProjectDir = File(savePointsDir,saveProjectName)
        val saveDir =File(eachProjectDir,name)
        val messageFile = File(eachProjectDir, "$name.txt")

        if (saveDir.exists()) {
            Messages.showErrorDialog("Save point '$name' already exists.", "Error")
            return false
        }
        else saveDir.mkdirs()
// root -> Save in case of this we are not replacing we are creating
        try {
            FileDocumentManager.getInstance().saveAllDocuments()
            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
            val future: Future<*> = executor.submit {
                if(rootDir.exists()){
                    fileCopier.copyDirectory(rootDir.toPath(), saveDir.toPath())
                }
                if((File(eachProjectDir,"preRollback")).exists()){
                    deleteFile(File(eachProjectDir,"preRollback"))
                    File(eachProjectDir, "preRollback.txt").delete()
                }

            }
            messageFile.writeText("$timestamp\n$message")
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

    fun rollbackToSavePoint(savePointName: String) {
        val saveProjectName = sanitizeFolderName(getProjectRoot().toString())
        val eachProjectSave =File(savePointsDir,saveProjectName)
        val timestamp = getTimestamp()
        val rootDir = getProjectRoot() // project itself
        val saveDir = File(eachProjectSave,savePointName) // each instance of project


        if (!saveDir.exists()) {
            Messages.showErrorDialog("Save point '$savePointName' not found.", "Error")
            return
        }
        val preRollbackMessageFile = File(eachProjectSave, "preRollback.txt")
        val preRollBackFile = File(eachProjectSave,"preRollback")
// save -> root here replace not create
        try {
            FileDocumentManager.getInstance().saveAllDocuments()
            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
            // Backup current state before rollback
            val future: Future<*> = executor.submit {
               if (preRollBackFile.exists()){
                   deleteFile(preRollBackFile)
               }
               else {preRollBackFile.mkdirs()}
                rootDir?.let { fileCopier.copyDirectory(it.toPath(), preRollBackFile.toPath()) }
                preRollbackMessageFile.writeText("preRollBack : $timestamp")
            }
            future.get() // Wait for the task to complete
            if (rootDir != null) {
                deleteDirectoryContents(rootDir)
            }
            rootDir?.let { fileCopier.copyDirectory(saveDir.toPath(), it.toPath()) }
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to rollback to save point: ${e.message}", "Error")
        } catch (e: InterruptedException) {
            Messages.showErrorDialog("Rollback was interrupted.", "Error")
        } catch (e: ExecutionException) {
            Messages.showErrorDialog("Failed to rollback to save point: ${e.message}", "Error")
        }
    }

    fun undoRollback(): Boolean {
        val saveProjectName = sanitizeFolderName(getProjectRoot().toString())
        val eachProjectSave =File(savePointsDir,saveProjectName)
        val rootDir = getProjectRoot()
        val preRollBackDir = File(eachProjectSave, "preRollback")
        if (!preRollBackDir.exists()) return false

//    preRollBack -> root
        try {
            if (rootDir != null) {
                deleteDirectoryContents(rootDir)
            }

            val future: Future<*> = executor.submit {
                rootDir?.let { fileCopier.copyDirectory(preRollBackDir.toPath(), it.toPath()) }
            }
            future.get()
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

        // Delete the preRollback directory and the associated text file
        deleteFile(preRollBackDir)
        File(eachProjectSave, "preRollback.txt").delete()

        return true
    }

//recursive delete file
private fun deleteFile(file: File) {
    if (!file.exists()) {
        println("File or directory does not exist: ${file.absolutePath}")
        return
    }

    if (file.isDirectory) {
        file.listFiles()?.forEach { deleteFile(it) }
    }

    try {
        if (file.delete()) {
            println("Successfully deleted: ${file.absolutePath}")
        } else {
            println("Failed to delete: ${file.absolutePath}")
        }
    } catch (e: SecurityException) {
        println("Security exception deleting file: ${file.absolutePath}, Error: ${e.message}")
    } catch (e: IOException) {
        println("I/O error deleting file: ${file.absolutePath}, Error: ${e.message}")
    }
}

    private fun deleteDirectoryContents(directory: File) {
        if (!directory.exists()) {
            println("Directory does not exist: ${directory.absolutePath}")
            return
        }

        if (!directory.isDirectory) {
            println("The specified path is not a directory: ${directory.absolutePath}")
            return
        }

        // Delete all files and subdirectories inside the directory
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirectoryContents(file) // Recursively delete contents of subdirectories
            }

            try {
               file.delete()
            } catch (e: SecurityException) {
                println("Security exception deleting file: ${file.absolutePath}, Error: ${e.message}")
            } catch (e: IOException) {
                println("I/O error deleting file: ${file.absolutePath}, Error: ${e.message}")
            }
        }
    }


    fun getSavePoints(): List<Pair<String, String>> {
        val saveProjectName = sanitizeFolderName(getProjectRoot().toString())
        val eachProjectSave = File(savePointsDir, saveProjectName)

        return eachProjectSave.listFiles()
            ?.filter {
                // Exclude .txt files and files named "preRollback"
                it.extension != "txt" && it.name != "preRollback"
            }
            ?.mapNotNull { file ->
                val name = file.name
                val messageFile = File(eachProjectSave, "${file.nameWithoutExtension}.txt")

                if (messageFile.exists()) {
                    val lines = messageFile.readLines()
                    val timestamp = lines.firstOrNull() ?: "Unknown"
                    val message = lines.drop(1).joinToString("\n").takeIf { it.isNotBlank() } ?: ""
                    Pair(name, "$timestamp\n$message")
                } else {
                    null
                }
            }
            ?.sortedByDescending { it.second.substringBefore('\n') }
            ?: emptyList()
    }

    fun deleteSavePoint(name: String):Boolean {
        val saveProjectName = sanitizeFolderName(getProjectRoot().toString())
        val eachProjectSave =File(savePointsDir,saveProjectName)
        val saveDir = File(eachProjectSave, name)
        val savePointTxtFile = File(eachProjectSave, "$name.txt")
        deleteFile(saveDir)
        if (saveDir.exists()) {
            Messages.showErrorDialog("Failed to delete save point '$name'.", "Error")
            return false
        }

        if (savePointTxtFile.exists()) {
            savePointTxtFile.delete()
        }
        return true
    }

    fun backupProject() {
        val rootDir = getProjectRoot() ?: run {
            Messages.showErrorDialog("Failed to get project root.", "Error")
            return
        }

        val timestamp = getTimestamp()
        val backupDir = getProjectBackupDir()
        val backupFile = File(backupDir, "backup")
        val messageFile = File(backupDir,"message.txt")

        try {

            if (backupFile.exists()) {
                backupFile.deleteRecursively()
            }
            backupFile.mkdirs()
            messageFile.writeText("Backup : $timestamp")

            // Perform backup in a separate thread to avoid blocking the UI thread
            FileDocumentManager.getInstance().saveAllDocuments()
            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
            val future: Future<*> = executor.submit {
                fileCopier.copyDirectory(rootDir.toPath(), backupFile.toPath())
            }
            future.get() // Wait for the task to complete
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to backup project: ${e.message}", "Error")
        } catch (e: InterruptedException) {
            Messages.showErrorDialog("Backup was interrupted.", "Error")
        } catch (e: ExecutionException) {
            Messages.showErrorDialog("Failed to backup project: ${e.message}", "Error")
        }
    }

    fun getBackUpFilesAddress(): Pair<String, String> {
        val saveProjectName = sanitizeFolderName(getProjectRoot().toString())
        val path1 = "${File(getSavePointsDir(),saveProjectName)}"
        val path2 = "${getProjectBackupDir()}"
        return Pair(path1, path2)
    }


//    fun sanitizeFolderName(input: String): String {
//        return input.replace("\\", "-").toString()
//            .replace(':', '-')
//            .replace("//", "-").toString()
//    }


    fun sanitizeFolderName(name: String): String {
        // Replace restricted characters with underscores
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }



    fun restore(): Boolean {
        val backupDir = File(getProjectBackupDir(), "backup")
        val rootDir = getProjectRoot()
//    preRollBack -> root
        try {
            if (rootDir != null) {
                deleteDirectoryContents(rootDir)
            }

            val future: Future<*> = executor.submit {
                rootDir?.let {
                    fileCopier.copyDirectory(backupDir.toPath(), it.toPath())
                }
                return@submit
            }
            future.get() // Wait for the task to complete
            return true
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to undo restore: ${e.message}", "Error")
            return false
        } catch (e: InterruptedException) {
            Messages.showErrorDialog("Undo restore was interrupted.", "Error")
            return false
        } catch (e: ExecutionException) {
            Messages.showErrorDialog("Failed to undo restore: ${e.message}", "Error")
            return false
        }
    }


    private fun isDirectoryEmpty(dirPath: Path): Boolean {
        if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
            Files.list(dirPath).use { stream ->
                return !stream.iterator().hasNext()
            }
        }
        return false
    }

}

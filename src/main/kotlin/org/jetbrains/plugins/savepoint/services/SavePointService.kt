package org.jetbrains.plugins.savepoint.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Service(Service.Level.PROJECT)
class SavePointService(private val project: Project) {

    private val savePointDir: File = File(project.basePath, ".savepoints")

    init {
        try {
            if (!savePointDir.exists()) {
                if (!savePointDir.mkdirs()) { // Create directories including any missing parent directories
                    Messages.showErrorDialog("Failed to create save point directory.", "Error")
                }
            }
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to initialize save point directory: ${e.message}", "Error")
        }
    }

    private fun getProjectRoot(): File? {
        // Use the basePath from the project to determine the root directory
        return project.basePath?.let { File(it) }
    }

    fun addSavePoint(message: String) {
        val rootDir = getProjectRoot() ?: run {
            Messages.showErrorDialog("Could not find the root directory of the project.", "Error")
            return
        }

        val savePointName = message
        if (savePointName.isNullOrEmpty()) {
            Messages.showErrorDialog("Save point name cannot be empty.", "Error")
            return
        }

        try {
            // Construct the path to the 'src' directory/file
            val srcFile = File(rootDir, "src")

            // Debug: Print out paths
            println("Root Directory: ${rootDir.absolutePath}")
            println("Source File: ${srcFile.absolutePath}")

            // Check if 'src' exists
            if (!srcFile.exists()) {
                Messages.showErrorDialog("The 'src' directory or file does not exist in the root directory.", "Error")
                return
            }

            // Prepare for zipping
            val savePointFile = File(savePointDir, "$savePointName.zip")
            if (savePointFile.exists()) {
                Messages.showErrorDialog("Save point '$savePointName' already exists.", "Error")
                return
            }

            val tempFile = File.createTempFile("savepoint", ".zip")

            // Zip the 'src' directory or file as a top-level entry
            ZipOutputStream(FileOutputStream(tempFile)).use { zipOut ->
                if (srcFile.isDirectory) {
                    // Add 'src' directory itself to the zip
                    zipOut.putNextEntry(ZipEntry("src/"))
                    zipOut.closeEntry()

                    // Add contents of 'src' directory
                    Files.walk(srcFile.toPath()).forEach { path ->
                        val file = path.toFile()
                        if (!file.isDirectory) {
                            val entryName = "src/" + srcFile.toPath().relativize(path).toString().replace("\\", "/")
                            zipOut.putNextEntry(ZipEntry(entryName))

                            FileInputStream(file).use { fis ->
                                fis.copyTo(zipOut)
                            }

                            zipOut.closeEntry()
                        }
                    }
                } else {
                    // Add 'src' file as a top-level entry
                    zipOut.putNextEntry(ZipEntry("src/${srcFile.name}"))
                    FileInputStream(srcFile).use { fis ->
                        fis.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }

            // Move the temp zip file to the save point directory
            Files.move(tempFile.toPath(), savePointFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

            Messages.showInfoMessage("Save point '$savePointName' created successfully.", "Save Point Added")
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to create save point: ${e.message}", "Error")
        }
    }



    fun rollbackToSavePoint(savePointName: String) {
        val savePoints = getSavePoints()
        println("Available save points: ${savePoints.map { it.nameWithoutExtension }}")

        val savePointFile = savePoints.find { it.nameWithoutExtension == savePointName } ?: run {
            println("Save point '$savePointName' not found.")
            Messages.showErrorDialog("Save point '$savePointName' not found.", "Error")
            return
        }

        try {
            val rootDir = getProjectRoot() ?: throw IOException("Project base path is null.")
            val targetDir = rootDir // Use the project root directory as the target directory

            // Debugging
            println("Target directory: ${targetDir.absolutePath}")

            // Delete the `src` file or directory within the target directory if it exists
            val srcFile = File(targetDir, "src")
            if (srcFile.exists()) {
                println("Deleting 'src' directory/file: ${srcFile.absolutePath}")
                deleteFile(srcFile)
            }

            // Unzip the file into the target directory
            println("Unzipping file: ${savePointFile.absolutePath} to ${targetDir.absolutePath}")
            unzipFile(savePointFile, targetDir)

            // Refresh the virtual file system
            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)

            Messages.showInfoMessage("Rolled back to save point: $savePointName", "Rollback")
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to rollback to save point: ${e.message}", "Error")
            e.printStackTrace()
        }
    }

    private fun deleteFile(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteFile(child) // Delete contents of the directory
            }
        }
        if (!file.delete()) {
            println("Failed to delete: ${file.absolutePath}")
        }
    }

    private fun unzipFile(zipFile: File, targetDir: File) {
        try {
            if (!zipFile.exists()) {
                throw IOException("Zip file does not exist: ${zipFile.absolutePath}")
            }

            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw IOException("Failed to create target directory: ${targetDir.absolutePath}")
            }

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipInput ->
                var entry: ZipEntry? = zipInput.nextEntry
                while (entry != null) {
                    val file = File(targetDir, entry.name)

                    if (entry.isDirectory) {
                        if (!file.mkdirs()) {
                            println("Failed to create directory: ${file.absolutePath}")
                        }
                    } else {
                        // Ensure parent directories exist
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fos ->
                            zipInput.copyTo(fos)
                        }
                    }
                    zipInput.closeEntry()
                    entry = zipInput.nextEntry
                }
            }
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to unzip file: ${e.message}", "Error")
            e.printStackTrace()
        }
    }


    fun showSavePoints() {
        val savePoints = getSavePoints()
        if (savePoints.isEmpty()) {
            Messages.showInfoMessage("No save points available.", "Save Points")
            return
        }

        val savePointNames = savePoints.map { it.nameWithoutExtension }
        Messages.showMessageDialog(
            savePointNames.joinToString("\n"),
            "Available Save Points",
            Messages.getInformationIcon()
        )
    }

    fun getSavePoints(): List<File> {
        return try {
            val files = savePointDir.listFiles()
            files?.filter { it.isFile && it.name.endsWith(".zip") }?.sortedBy { it.name } ?: emptyList()
        } catch (e: IOException) {
            Messages.showErrorDialog("Failed to list save points: ${e.message}", "Error")
            emptyList()
        }
    }
}

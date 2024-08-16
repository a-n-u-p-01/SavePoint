package org.anupam.plugins.savepoint.utils

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException
import java.util.logging.Logger

class AdvancedFileCopier(private val logger: Logger) {

    fun copyDirectory(sourceDir: Path, targetDir: Path) {
        try {
            // Create the target directory if it does not exist
            Files.createDirectories(targetDir)
            // Walk the source directory tree
            Files.walkFileTree(sourceDir, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val targetPath = targetDir.resolve(sourceDir.relativize(dir))
                    // Create the target directory if it does not exist
                    if (Files.notExists(targetPath)) {
                        Files.createDirectories(targetPath)
                        logger.info("Created directory: $targetPath")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val targetPath = targetDir.resolve(sourceDir.relativize(file))
                    try {
                        Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING)
                        logger.info("Copied file: $file to $targetPath")
                    } catch (e: IOException) {
                        logger.severe("Failed to copy file $file: ${e.message}")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    logger.severe("Failed to visit file $file: ${exc.message}")
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: IOException) {
            logger.severe("Error copying directory: ${e.message}")
            throw e
        }
    }
}
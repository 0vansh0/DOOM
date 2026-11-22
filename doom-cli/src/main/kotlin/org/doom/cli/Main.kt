package org.doom.cli

import org.doom.engine.ModPackage
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val manifest = when {
            args.size == 2 && args[0] == "validate" -> ModPackage.validate(File(args[1]))
            args.size == 3 && args[0] == "pack" -> pack(File(args[1]), File(args[2]))
            else -> error("Usage: validate <file.doommod> | pack <source-directory> <file.doommod>")
        }
        println("${manifest.id} ${manifest.version} — ${manifest.title}")
    } catch (error: Exception) {
        System.err.println(error.message)
        exitProcess(1)
    }
}

private fun pack(source: File, output: File): org.doom.model.ModManifest {
    require(source.isDirectory) { "Source directory is missing." }
    require(output.extension == "doommod") { "Expected a .doommod output file." }
    require(!output.exists()) { "Output file already exists." }
    val staged = File.createTempFile("doom-pack-", ".zip", output.absoluteFile.parentFile)
    try {
        val manifests = listOf(source.resolve("doom.json"), source.resolve("mod.json"))
        val assets = source.resolve("assets").listFiles().orEmpty().sortedBy { it.name }
        require(assets.size <= 8) { "Too many assets." }
        ZipOutputStream(staged.outputStream()).use { zip ->
            (manifests + assets).forEach { file ->
                require(file.isFile && file.length() <= 65_536) { "Invalid source file." }
                zip.putNextEntry(ZipEntry(file.relativeTo(source).invariantSeparatorsPath).apply { time = 0 })
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val manifest = ModPackage.validate(staged)
        java.nio.file.Files.move(staged.toPath(), output.toPath())
        return manifest
    } finally {
        staged.delete()
    }
}

package daggerok

import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import kotlin.io.path.absolutePathString
import kotlin.reflect.jvm.jvmName
import org.apache.logging.log4j.kotlin.logger
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

data class FileItem(
    val name: String = "",
    val relativePath: String = "webflux-file-server",
    val baseDirectory: String = "target",
    val path: String = Paths.get(baseDirectory, relativePath, name).normalize()
        .absolutePathString(),
) {
    init {
        if (name.isBlank()) throw RuntimeException("file item name may not be empty")
        if (relativePath.isBlank()) throw RuntimeException("file item relative path may not be empty")
        if (baseDirectory.isBlank()) throw RuntimeException("file item base directory may not be empty")
    }
}

@Configuration
class FilesConfig {

    @Bean
    fun localStorage() = mutableMapOf<String, FileItem>()
}

@Repository
class Files(private val localStorage: MutableMap<String, FileItem>) {

    fun save(fileItem: FileItem) {
        localStorage[fileItem.path] = fileItem
    }

    fun findAll() = localStorage.values.toList()
}

@RestController
class FilesResource(private val files: Files) {

    @GetMapping
    fun index() = files.findAll()

    @PostMapping("/upload")
    fun upload(@RequestPart("file") filePart: FilePart, serverHttpRequest: ServerHttpRequest) =
        FileItem(name = filePart.filename()).run {
            Mono.fromCallable { this.create(filePart) }
        }

    @GetMapping(path = ["/download/{filename}"], produces = [APPLICATION_OCTET_STREAM_VALUE])
    fun download(@PathVariable("filename") filename: String) =
        Mono
            .fromCallable {
                val path = FileItem(name = filename).path
                FileSystemResource(path)
            }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { fsResource ->
                val headers = HttpHeaders().apply {
                    setContentDispositionFormData(filename, filename)
                }
                Mono.just(
                    ResponseEntity.ok()
                        .cacheControl(CacheControl.noCache())
                        .headers(headers)
                        .body(fsResource)
                )
            }
}

private val log = logger(Application::class.jvmName)

fun FileItem.create(filePart: FilePart) =
    kotlin
        .runCatching {
            val path = Paths.get(path).also {
                it.parent.toFile().mkdirs()
                java.nio.file.Files.deleteIfExists(it)
            }
            val filePath = java.nio.file.Files.createFile(path)
            val asynchronousFileChannel = AsynchronousFileChannel.open(filePath, CREATE, TRUNCATE_EXISTING, WRITE)
            DataBufferUtils
                .write(filePart.content(), asynchronousFileChannel, 0).doOnComplete {
                    val result = runCatching { asynchronousFileChannel.close() }
                    result.onFailure { log.warn(it) { it.message } }
                }
                .subscribe()
        }
        .onSuccess { log.info { "File ${filePart.filename()} created" } }
        .onFailure { log.error(it) { it.message } }
        .isSuccess

package org.spectral.deobfuscator

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import org.spectral.deobfuscator.asm.ClassGroupExt
import org.spectral.deobfuscator.transformer.*
import org.spectral.deobfuscator.transformer.rename.NameGenerator
import org.spectral.deobfuscator.transformer.controlflow.ControlFlowFixer
import org.spectral.deobfuscator.transformer.euclidean.MultiplierRemover
import org.tinylog.kotlin.Logger
import java.io.File

/**
 * Represents the Spectral OSRS gamepack deobfuscator object.
 *
 * @constructor
 */
class Deobfuscator {

    lateinit var group: ClassGroupExt

    /**
     * The transformers in order to run.
     */
    val transformers = mutableListOf<Transformer>()

    /**
     * Register the transformer instances.
     */
    init {
        register(UnusedMethodRemover())
        register(UnusedFieldRemover())
        register(MultiplierRemover())
        register(ControlFlowFixer())
        register(FieldInliner())
        register(TryCatchRemover())
        register(ErrorConstructorRemover())
        register(GotoRemover())
        register(DeadCodeRemover())
        register(OpaquePredicateCheckRemover())
        register(FieldSorter())
        register(MethodSorter())
        register(NameGenerator())
        register(OpaquePredicateArgRemover())
    }

    private fun register(transformer: Transformer) {
        transformers.add(transformer)
    }

    fun loadInputJar(inputFile: File) {
        Logger.info("Loading input JAR file: '${inputFile.path}' classes.")
        group = ClassGroupExt.fromJar(inputFile)
        Logger.info("Successfully loaded ${group.size} classes.")
    }

    /**
     * Runs the deobfuscation with a [Unit] consumer call back.
     *
     * @param clean Whether to skip the name generator transformer.
     * @param consumer Function0<Unit>
     */
    fun run(clean: Boolean = false, consumer: () -> Unit) {
        /*
         * Apply each transformer
         */
        Logger.info("Preparing to run bytecode transformers.")

        transformers.forEach { transformer ->
            if(clean && transformer is NameGenerator) {
                return@forEach
            }

            consumer()
            Logger.info("Running bytecode transformer '${transformer::class.simpleName}'...")
            transformer.transform(group)
        }
    }

    /**
     * Runs the deobfuscation with an empty consumer [Unit] callback.
     *
     * @param clean Whether to skip the name generator transformer.
     */
    fun run(clean: Boolean = false) = this.run {}

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = object : CliktCommand(
            name = "Deobfuscator",
            help = "Deobfuscates a Jagex OSRS gamepack to enable it to be decompiled and reverse-engineered.",
            printHelpOnEmptyArgs = true,
            invokeWithoutSubcommand = true
        ) {

            private val inputFile by argument(name = "input file", help = "The obfuscated input JAR file.").file(mustExist = true, canBeDir = false)
            private val outputFile by argument(name = "output file", help = "The output JAR file to export to.").file(mustExist = false, canBeDir = false)

            private val clean by option("-c", "--clean", help = "Disables the name generator transformer.").flag(default = false)

            private inline fun <R> ProgressBar.run(block: (ProgressBar) -> R): R {
                var exception: Throwable? = null
                try {
                    return block(this)
                } catch(e : Throwable) {
                    exception = e
                    throw e
                } finally {
                    when (exception) {
                        null -> close()
                        else -> {
                            try {
                                close()
                            } catch (closeException: Throwable) {}
                        }
                    }
                }
            }

            /**
             * Run the deobfuscator with progress bar call back when executing from
             * the command line.
             */
            override fun run() {
                Logger.info("Preparing deobfuscator...")

                val deobfuscator = Deobfuscator()
                deobfuscator.loadInputJar(inputFile)

                /*
                 * Create progress bar
                 */
                val progress = ProgressBarBuilder()
                    .setInitialMax(deobfuscator.transformers.size.toLong() + 1L)
                    .setTaskName("Deobfuscating")
                    .build()

                progress.run { p ->
                    deobfuscator.run(clean) {
                        p.step()
                    }

                    /*
                      * Export to the outputFile
                     */
                    p.step()

                    deobfuscator.group.toJar(outputFile)

                    return
                }
            }
        }.main(args)
    }
}
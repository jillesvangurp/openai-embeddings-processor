package com.jillesvangurp

import com.aallam.openai.api.embedding.EmbeddingRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File
import java.util.*

val logger= KotlinLogging.logger {}
@OptIn(KotlinCsvExperimental::class)
fun main(args: Array<String>) {
    val argParser = ArgParser("openai-embeddings-processor")

    val tokenProperty by argParser.option(
        type = ArgType.String,
        shortName = "t",
        description = "your openai token",
        fullName = "token"
    )
    val modelId by argParser.option(ArgType.String,"model", "m").default("text-similarity-ada-001")
    val input by argParser.option(ArgType.String, "input", "i", "Input file").default("input.tsv")
    val output by argParser.option(ArgType.String, "output", "o", "Output file").default("embeddings.tsv")
    val chunkSize by argParser.option(ArgType.Int,"chunk-size").default(5)

    argParser.parse(args)

    val token = tokenProperty?: run {
        File("local.properties").takeIf { it.isFile && it.exists() }?.let {
            val properties = Properties()
            properties.load(it.reader())
            properties.getProperty("openai.token")
        }
    } ?: System.getenv("OPENAI_TOKEN") ?: error("no openai token. Either call with -t <token>; create a local.properties file with an openai.token property; or set the OPENAI_TOKEN environment variable")

    logger.info { "will process $input to $output with chunk size $chunkSize and using model $modelId" }
    val openAI = OpenAI(token)

    val rows: List<Map<String, String>> = csvReader {
        delimiter = '\t'
    }.readAllWithHeader(File(input))
    logger.info { "read ${rows.size} rows from $input" }
    val embeddings = rows.filter {row->
        val id = row["id"]
        val text = row["text"]
        !(id.isNullOrBlank() || text.isNullOrBlank())
    }.chunked(chunkSize).flatMap { subRows ->
        val texts = subRows.mapNotNull { it["text"] }
        runBlocking {
            logger.info { "calling openai embeddings for $texts" }
            try {
                openAI.embeddings(EmbeddingRequest(ModelId(modelId), texts)).let {
                    it.embeddings.map {e ->
                        val id = subRows[e.index]["id"] ?: error("should not happen")
                        id to e.embedding
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "OpenAI call failed for $texts" }
                listOf()
            }
        }
    }

    csvWriter {
        delimiter='\t'

    }.openAndGetRawWriter(output).use {csvWriter ->
        csvWriter.writeRow(listOf("id","embedding"))
        embeddings.forEach { (id, embedding) ->
            logger.info { "embedding $id has ${embedding.size} dimensions" }
            csvWriter.writeRow(id, embedding.toString())
        }
    }
    logger.info { "done" }
}
package com.pablords.opensearch;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe para operações de busca semântica no OpenSearch.
 * Otimizada para produção com bulk indexing e validações.
 */
public class Bootstrap {

  private static final String INDEX_NAME = "semantic-search-demo";
  private static final String VECTOR_FIELD = "text_vector";

  public static void deleteIndexIfExists(OpenSearchClient client) throws Exception {
    if (client.indices().exists(new ExistsRequest.Builder().index(INDEX_NAME).build()).value()) {
      System.out.println("Deletando índice existente: " + INDEX_NAME);
      client.indices().delete(d -> d.index(INDEX_NAME));
      System.out.println("Índice deletado.");
    }
  }

  public static void createKnnIndex(OpenSearchClient client, int vectorDim) throws Exception {

    // Verificar se o índice já existe
    if (client.indices().exists(new ExistsRequest.Builder().index(INDEX_NAME).build()).value()) {
      System.out.println("Índice '" + INDEX_NAME + "' já existe. Pulando criação.");
      return;
    }

    System.out.println("Criando índice para busca híbrida (BM25 + Semântica): " + INDEX_NAME);

    // Criar o índice com k-NN habilitado e campos otimizados para BM25
    CreateIndexRequest createReq = new CreateIndexRequest.Builder()
        .index(INDEX_NAME)
        .settings(s -> s
            .index(i -> i
                .knn(true) // Habilitar k-NN no índice
            ))
        .mappings(m -> m
            // Campo de vetor para busca semântica
            .properties(VECTOR_FIELD, p -> p
                .knnVector(kv -> kv
                    .dimension(vectorDim)
                    .method(method -> method
                        .name("hnsw") // Algoritmo HNSW
                        .spaceType("cosinesimil") // Similaridade de cosseno
                        .engine("lucene"))))
            // Campo title para BM25 (com boost)
            .properties("title", p -> p
                .text(t -> t
                    .analyzer("standard") // Analisador padrão para português/inglês
                    .fields("keyword", f -> f.keyword(k -> k)))) // Subcampo keyword para exact match
            // Campo description para BM25
            .properties("description", p -> p
                .text(t -> t
                    .analyzer("standard")))
            // Campo category para filtros
            .properties("category", p -> p
                .keyword(k -> k)))
        .build();

    client.indices().create(createReq);
    System.out.println("✓ Índice criado com campos otimizados para busca híbrida");
    System.out.println("  - title: BM25 indexing com boost");
    System.out.println("  - description: BM25 indexing");
    System.out.println("  - " + VECTOR_FIELD + ": k-NN semântica (HNSW + cosine)");
    System.out.println("  - category: filtros exatos");
  }

  public static void indexDocuments(OpenSearchClient client, EmbeddingModel model) throws Exception {
    System.out.println("\n--- Indexando Documentos ---");

    ObjectMapper mapper = new ObjectMapper();
    List<Map<String, String>> products = mapper.readValue(
        new File("data/products_synthetic.json"),
        new TypeReference<List<Map<String, String>>>() {
        });

    indexDocumentsBatch(client, model, products);
  }

  /**
   * Indexa documentos em batch usando Bulk API para melhor performance
   * 
   * @param client Cliente OpenSearch
   * @param model  Modelo de embedding
   * @param texts  Lista de produtos (Map com title, description, category)
   */
  public static void indexDocumentsBatch(OpenSearchClient client, EmbeddingModel model, List<Map<String, String>> texts)
      throws Exception {
    if (texts == null || texts.isEmpty()) {
      System.out.println("Nenhum documento para indexar.");
      return;
    }

    System.out.println("Gerando embeddings em batch para " + texts.size() + " documentos...");
    long startTime = System.currentTimeMillis();

    // Gerar todos os embeddings em batch (mais eficiente)
    List<float[]> embeddings = model.embedBatch(texts);

    long embeddingTime = System.currentTimeMillis() - startTime;
    System.out.println("Embeddings gerados em " + embeddingTime + "ms");

    // Criar requisição Bulk
    BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

    for (int i = 0; i < texts.size(); i++) {
      final int docIndex = i;
      Map<String, String> product = texts.get(i);
      String title = product.get("title");
      String description = product.get("description");
      String category = product.get("category");
      float[] vector = embeddings.get(i);

      // Criar documento com TODOS os campos para busca híbrida
      Map<String, Object> docBody = new HashMap<>();
      docBody.put("title", title);
      docBody.put("description", description);
      docBody.put("category", category);
      docBody.put(VECTOR_FIELD, vector);


      // Adicionar ao bulk
      bulkBuilder.operations(op -> op
          .index(idx -> idx
              .index(INDEX_NAME)
              .id("doc_" + docIndex)
              .document(docBody)));
    }

    // Executar bulk indexing
    System.out.println("Indexando " + texts.size() + " documentos via Bulk API...");
    BulkResponse response = client.bulk(bulkBuilder.build());

    // Verificar erros
    if (response.errors()) {
      System.err.println("Erros durante bulk indexing:");
      for (BulkResponseItem item : response.items()) {
        if (item.error() != null) {
          System.err.println("Erro no documento " + item.id() + ": " + item.error().reason());
        }
      }
    } else {
      System.out.println("✓ " + texts.size() + " documentos indexados com sucesso!");
    }

    long totalTime = System.currentTimeMillis() - startTime;
    System.out.println("Tempo total: " + totalTime + "ms (" + (totalTime / texts.size()) + "ms por documento)");

    // Refresh do índice
    System.out.println("Refreshing índice...");
    client.indices().refresh(r -> r.index(INDEX_NAME));
  }

}
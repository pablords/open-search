package com.pablords.opensearch;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.KnnQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe para operações de busca semântica no OpenSearch.
 * Otimizada para produção com bulk indexing e validações.
 */
public class SemanticSearchOpenSearch {

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

    System.out.println("Criando índice: " + INDEX_NAME);

    // Criar o índice com k-NN habilitado
    // IMPORTANTE: Precisa habilitar index.knn nas settings
    CreateIndexRequest createReq = new CreateIndexRequest.Builder()
        .index(INDEX_NAME)
        .settings(s -> s
            .index(i -> i
                .knn(true) // Habilitar k-NN no índice
            )
        )
        .mappings(m -> m
            .properties(VECTOR_FIELD, p -> p
                .knnVector(kv -> kv
                    .dimension(vectorDim)
                    .method(method -> method
                        .name("hnsw") // Algoritmo HNSW
                        .spaceType("cosinesimil") // Similaridade de cosseno
                        .engine("lucene")
                    )
                )
            )
            .properties("text_original", p -> p
                .text(t -> t)
            )
        )
        .build();

    client.indices().create(createReq);
    System.out.println("Índice criado com sucesso.");
  }

  public static void indexDocuments(OpenSearchClient client, EmbeddingModel model) throws Exception {
    System.out.println("\n--- Indexando Documentos ---");

    List<String> docs = Arrays.asList(
        "Meu cachorro é meu melhor amigo. Ele ama correr.", // Doc 1
        "O gato está dormindo no raio de sol.", // Doc 2
        "Eu amo cozinhar pizza de pepperoni em casa.", // Doc 3
        "Como treinar seu novo filhote de cão." // Doc 4
    );

    indexDocumentsBatch(client, model, docs);
  }
  
  /**
   * Indexa documentos em batch usando Bulk API para melhor performance
   * @param client Cliente OpenSearch
   * @param model Modelo de embedding
   * @param texts Lista de textos para indexar
   */
  public static void indexDocumentsBatch(OpenSearchClient client, EmbeddingModel model, List<String> texts) throws Exception {
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
      final int docIndex = i; // Variável final para usar na lambda
      String text = texts.get(i);
      float[] vector = embeddings.get(i);
      
      // Criar documento
      Map<String, Object> docBody = new HashMap<>();
      docBody.put("text_original", text);
      docBody.put(VECTOR_FIELD, vector);
      
      // Adicionar ao bulk
      bulkBuilder.operations(op -> op
          .index(idx -> idx
              .index(INDEX_NAME)
              .id("doc_" + docIndex)
              .document(docBody)
          )
      );
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

  public static void searchByVector(OpenSearchClient client, EmbeddingModel model, String queryText) throws Exception {
    searchByVector(client, model, queryText, 3);
  }
  
  /**
   * Busca semântica por vetor com número customizado de resultados
   * @param client Cliente OpenSearch
   * @param model Modelo de embedding
   * @param queryText Texto da query
   * @param k Número de resultados desejados
   */
  public static void searchByVector(OpenSearchClient client, EmbeddingModel model, String queryText, int k) throws Exception {
    if (queryText == null || queryText.trim().isEmpty()) {
      throw new IllegalArgumentException("Query text não pode ser nulo ou vazio");
    }
    
    if (k <= 0) {
      throw new IllegalArgumentException("k deve ser maior que 0");
    }
    
    System.out.println("\n--- Busca Semântica por: '" + queryText + "' (top " + k + ") ---");
    long startTime = System.currentTimeMillis();

    // 1. Gerar o vetor da *query* (com cache)
    float[] queryVector = model.embed(queryText);
    long embeddingTime = System.currentTimeMillis() - startTime;
    System.out.println("Vetor de busca gerado em " + embeddingTime + "ms");

    // 2. Construir a Query k-NN
    KnnQuery knnQuery = new KnnQuery.Builder()
        .field(VECTOR_FIELD)
        .vector(queryVector)
        .k(k)
        .build();

    Query query = new Query.Builder().knn(knnQuery).build();

    // 3. Executar a Busca
    SearchRequest searchReq = new SearchRequest.Builder()
        .index(INDEX_NAME)
        .query(query)
        .size(k)
        .build();

    long searchStartTime = System.currentTimeMillis();
    var response = client.search(searchReq, Map.class);
    long searchTime = System.currentTimeMillis() - searchStartTime;

    System.out.println("Busca executada em " + searchTime + "ms");
    System.out.println("Total de resultados: " + response.hits().total().value());

    // 4. Exibir Resultados
    if (response.hits().hits().isEmpty()) {
      System.out.println("❌ Nenhum resultado encontrado.");
    } else {
      System.out.println("\n📊 Resultados:");
      int rank = 1;
      for (Hit<?> hit : response.hits().hits()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) hit.source();
        System.out.printf(
            "%d. Score: %.4f | %s\n",
            rank++,
            hit.score(),
            source.get("text_original"));
      }
    }
    
    long totalTime = System.currentTimeMillis() - startTime;
    System.out.println("\n⏱️  Tempo total: " + totalTime + "ms (embedding: " + embeddingTime + "ms + busca: " + searchTime + "ms)");
    
    // Mostrar estatísticas do cache
    System.out.println("📦 " + model.getCacheStats());
  }
}
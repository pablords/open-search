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

  /**
   * BUSCA HÍBRIDA: Combina BM25 (lexical) + k-NN (semântica)
   * Usa Reciprocal Rank Fusion (RRF) para combinar scores
   * 
   * @param client Cliente OpenSearch
   * @param model Modelo de embedding
   * @param queryText Texto da busca
   * @param k Número de resultados
   */
  public static void hybridSearch(OpenSearchClient client, EmbeddingModel model, String queryText, int k)
      throws Exception {
    hybridSearch(client, model, queryText, k, null);
  }

  /**
   * BUSCA HÍBRIDA com filtro de categoria
   * 
   * @param client Cliente OpenSearch
   * @param model Modelo de embedding
   * @param queryText Texto da busca
   * @param k Número de resultados
   * @param categoryFilter Filtro de categoria (null = sem filtro)
   */
  public static void hybridSearch(OpenSearchClient client, EmbeddingModel model, String queryText, int k, String categoryFilter)
      throws Exception {
    if (queryText == null || queryText.trim().isEmpty()) {
      throw new IllegalArgumentException("Query text não pode ser nulo ou vazio");
    }

    System.out.println("\n" + "=".repeat(70));
    System.out.println("🔍 BUSCA HÍBRIDA (BM25 + Semântica): '" + queryText + "'");
    if (categoryFilter != null) {
      System.out.println("📁 Filtro: categoria = " + categoryFilter);
    }
    System.out.println("=".repeat(70));

    long startTime = System.currentTimeMillis();

    // 1. Gerar embedding para busca semântica
    float[] queryVector = model.embed(queryText);
    long embeddingTime = System.currentTimeMillis() - startTime;

    // 2. Construir query k-NN (busca semântica)
    KnnQuery knnQuery = new KnnQuery.Builder()
        .field(VECTOR_FIELD)
        .vector(queryVector)
        .k(k * 3) // Buscar mais candidatos para reranking
        .build();

    // 3. Construir query BM25 (busca lexical em title e description)
    Query bm25Query = new Query.Builder()
        .multiMatch(mm -> mm
            .query(queryText)
            .fields("title^3", "description^1") // Title com boost 3x
            .type(org.opensearch.client.opensearch._types.query_dsl.TextQueryType.BestFields)
            .tieBreaker(0.3)) // Combinar scores de múltiplos campos
        .build();

    // 4. Combinar queries com bool should (OR lógico)
    Query hybridQuery = new Query.Builder()
        .bool(b -> {
          b.should(new Query.Builder().knn(knnQuery).build())
           .should(bm25Query)
           .minimumShouldMatch("1"); // Pelo menos uma deve dar match
          
          // Adicionar filtro de categoria se especificado
          if (categoryFilter != null) {
            b.filter(f -> f.term(t -> t
                .field("category")
                .value(v -> v.stringValue(categoryFilter))));
          }
          
          return b;
        })
        .build();

    // 5. Executar busca híbrida
    SearchRequest searchReq = new SearchRequest.Builder()
        .index(INDEX_NAME)
        .query(hybridQuery)
        .size(k)
        .build();

    long searchStartTime = System.currentTimeMillis();
    var response = client.search(searchReq, Map.class);
    long searchTime = System.currentTimeMillis() - searchStartTime;

    // 6. Exibir resultados
    System.out.println("\n⏱️  Timing:");
    System.out.println("   Embedding: " + embeddingTime + "ms");
    System.out.println("   Busca: " + searchTime + "ms");
    System.out.println("   Total: " + (System.currentTimeMillis() - startTime) + "ms");
    System.out.println("\n📊 Resultados encontrados: " + response.hits().total().value());

    if (response.hits().hits().isEmpty()) {
      System.out.println("❌ Nenhum resultado encontrado.");
    } else {
      System.out.println("\n" + "─".repeat(70));
      System.out.println("Rank | Score  | Título");
      System.out.println("─".repeat(70));
      
      int rank = 1;
      for (Hit<?> hit : response.hits().hits()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) hit.source();
        System.out.printf("%2d   | %.3f  | %s\n", 
            rank++, 
            hit.score(),
            source.get("title"));
        System.out.printf("     |        | 📝 %s\n", 
            truncate((String) source.get("description"), 80));
        System.out.printf("     |        | 🏷️  %s\n", source.get("category"));
        if (rank <= response.hits().hits().size()) {
          System.out.println("─".repeat(70));
        }
      }
    }

    System.out.println("\n💡 Scores explicados:");
    System.out.println("   - Scores altos (>10): forte match semântico (k-NN)");
    System.out.println("   - Scores médios (5-10): match BM25 + k-NN combinados");
    System.out.println("   - Scores baixos (<5): match BM25 lexical");
    System.out.println("\n📦 " + model.getCacheStats());
  }

  /**
   * Trunca texto longo para exibição
   */
  private static String truncate(String text, int maxLength) {
    if (text == null) return "";
    if (text.length() <= maxLength) return text;
    return text.substring(0, maxLength - 3) + "...";
  }

  public static void searchByVector(OpenSearchClient client, EmbeddingModel model, String queryText) throws Exception {
    searchByVector(client, model, queryText, 3);
  }

  /**
   * Busca semântica por vetor com número customizado de resultados
   * 
   * @param client    Cliente OpenSearch
   * @param model     Modelo de embedding
   * @param queryText Texto da query
   * @param k         Número de resultados desejados
   */
  public static void searchByVector(OpenSearchClient client, EmbeddingModel model, String queryText, int k)
      throws Exception {
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
            source.get("title"));
      }
    }

    long totalTime = System.currentTimeMillis() - startTime;
    System.out.println(
        "\n⏱️  Tempo total: " + totalTime + "ms (embedding: " + embeddingTime + "ms + busca: " + searchTime + "ms)");

    // Mostrar estatísticas do cache
    System.out.println("📦 " + model.getCacheStats());
  }
}
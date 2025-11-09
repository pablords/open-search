package com.pablords.opensearch;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.KnnQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.opensearch.core.SearchRequest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ARQUITETURA COMPLETA: Busca Híbrida + LTR (Learning to Rank)
 * 
 * ETAPA 1: RETRIEVAL (Recuperação)
 * - Motor Léxico (BM25): Busca em title, description, category
 * - Motor Semântico (k-NN): Busca por similaridade de embeddings
 * - Retorna ~200 candidatos (Top 100 de cada, com dedup)
 * 
 * ETAPA 2: FEATURE EXTRACTION
 * - Extrai 17+ features de cada candidato
 * - Features: BM25, k-NN, exact match, term coverage, popularidade, etc
 * 
 * ETAPA 3: RE-RANKING (LTR)
 * - Aplica modelo LTR treinado
 * - Calcula score final
 * - Retorna Top K resultados ordenados
 */
public class HybridSearchWithLTR {

  private static final String INDEX_NAME = "semantic-search-demo";
  private static final String VECTOR_FIELD = "text_vector";
  private static final int RETRIEVAL_SIZE = 100; // Top 100 de cada motor

  private final OpenSearchClient client;
  private final EmbeddingModel embeddingModel;
  private final FeatureExtractor featureExtractor;
  private final LTRModel ltrModel;

  public HybridSearchWithLTR(OpenSearchClient client, EmbeddingModel embeddingModel) {
    this.client = client;
    this.embeddingModel = embeddingModel;
    this.featureExtractor = new FeatureExtractor();
    this.ltrModel = new LTRModel(); // Pesos padrão otimizados
  }

  public HybridSearchWithLTR(OpenSearchClient client, EmbeddingModel embeddingModel, LTRModel customModel) {
    this.client = client;
    this.embeddingModel = embeddingModel;
    this.featureExtractor = new FeatureExtractor();
    this.ltrModel = customModel;
  }

  /**
   * BUSCA COMPLETA COM LTR (3 Etapas)
   * 
   * @param queryText      Texto da busca do usuário
   * @param topK           Número de resultados finais
   * @param categoryFilter Filtro opcional de categoria
   * @return Lista de resultados ranqueados por LTR
   */
  public List<SearchResult> search(String queryText, int topK, String categoryFilter) throws Exception {
    long startTime = System.currentTimeMillis();

    System.out.println("\n" + "═".repeat(80));
    System.out.println("🔍 BUSCA HÍBRIDA + LTR: \"" + queryText + "\"");
    if (categoryFilter != null) {
      System.out.println("📁 Filtro: categoria = " + categoryFilter);
    }
    System.out.println("═".repeat(80));

    // ================================================================
    // ETAPA 1: RETRIEVAL - Busca Híbrida (BM25 + k-NN)
    // ================================================================
    System.out.println("\n📊 ETAPA 1: RETRIEVAL (Busca Híbrida)");
    System.out.println("─".repeat(80));

    long retrievalStart = System.currentTimeMillis();
    List<SearchResult> candidates = retrievalStage(queryText, categoryFilter);
    long retrievalTime = System.currentTimeMillis() - retrievalStart;

    System.out.println("✓ Motor BM25: Top " + RETRIEVAL_SIZE + " resultados léxicos");
    System.out.println("✓ Motor k-NN: Top " + RETRIEVAL_SIZE + " resultados semânticos");
    System.out.println("✓ Total de candidatos únicos: " + candidates.size());
    System.out.println("⏱️  Tempo: " + retrievalTime + "ms");

    if (candidates.isEmpty()) {
      System.out.println("\n❌ Nenhum resultado encontrado.");
      return Collections.emptyList();
    }

    // ================================================================
    // ETAPA 2: FEATURE EXTRACTION
    // ================================================================
    System.out.println("\n🔬 ETAPA 2: FEATURE EXTRACTION");
    System.out.println("─".repeat(80));

    long featureStart = System.currentTimeMillis();
    for (SearchResult result : candidates) {
      FeatureVector features = featureExtractor.extractFeatures(result, queryText, candidates);
      result.setFeatures(features);
    }
    long featureTime = System.currentTimeMillis() - featureStart;

    System.out.println("✓ Features extraídas: 17 features por documento");
    System.out.println("✓ Total de vetores: " + candidates.size());
    System.out.println("⏱️  Tempo: " + featureTime + "ms (" +
        String.format("%.2f", (double) featureTime / candidates.size()) + "ms por doc)");

    // ================================================================
    // ETAPA 3: RE-RANKING com LTR
    // ================================================================
    System.out.println("\n🤖 ETAPA 3: RE-RANKING (LTR)");
    System.out.println("─".repeat(80));

    long rerankStart = System.currentTimeMillis();
    for (SearchResult result : candidates) {
      double ltrScore = ltrModel.predict(result.getFeatures());
      result.setLtrScore(ltrScore);
    }
    long rerankTime = System.currentTimeMillis() - rerankStart;

    // Ordenar por LTR score (descendente)
    candidates.sort((a, b) -> Double.compare(b.getLtrScore(), a.getLtrScore()));

    System.out.println("✓ Modelo LTR aplicado a todos os candidatos");
    System.out.println("✓ Resultados reordenados por score LTR");
    System.out.println("⏱️  Tempo: " + rerankTime + "ms");

    // Retornar Top K
    List<SearchResult> topResults = candidates.stream()
        .limit(topK)
        .collect(Collectors.toList());

    // ================================================================
    // RESUMO FINAL
    // ================================================================
    long totalTime = System.currentTimeMillis() - startTime;

    System.out.println("\n" + "═".repeat(80));
    System.out.println("⏱️  TIMING BREAKDOWN");
    System.out.println("─".repeat(80));
    System.out.println(String.format("   Retrieval (BM25+k-NN)  : %5dms  (%.1f%%)",
        retrievalTime, 100.0 * retrievalTime / totalTime));
    System.out.println(String.format("   Feature Extraction     : %5dms  (%.1f%%)",
        featureTime, 100.0 * featureTime / totalTime));
    System.out.println(String.format("   LTR Re-ranking         : %5dms  (%.1f%%)",
        rerankTime, 100.0 * rerankTime / totalTime));
    System.out.println("   " + "─".repeat(40));
    System.out.println(String.format("   TOTAL                  : %5dms", totalTime));
    System.out.println("═".repeat(80));

    // Exibir resultados
    displayResults(topResults, queryText);

    return topResults;
  }

  /**
   * ETAPA 1: Retrieval - Busca Híbrida
   * Combina BM25 (léxico) + k-NN (semântico)
   */
  private List<SearchResult> retrievalStage(String queryText, String categoryFilter) throws Exception {
    // 1. Gerar embedding para k-NN
    float[] queryVector = embeddingModel.embed(queryText);

    // 2. Query k-NN (busca semântica)
    KnnQuery knnQuery = new KnnQuery.Builder()
        .field(VECTOR_FIELD)
        .vector(queryVector)
        .k(RETRIEVAL_SIZE)
        .build();

    // 3. Query BM25 (busca léxica em title, description, category)
    Query bm25Query = new Query.Builder()
        .multiMatch(mm -> mm
            .query(queryText)
            .fields("title^3", "description^1.5", "category^0.5")
            .type(TextQueryType.BestFields)
            .tieBreaker(0.3))
        .build();

    // 4. Combinar com bool should
    Query hybridQuery = new Query.Builder()
        .bool(b -> {
          b.should(new Query.Builder().knn(knnQuery).build())
              .should(bm25Query)
              .minimumShouldMatch("1");

          // Filtro de categoria se especificado
          if (categoryFilter != null) {
            b.filter(f -> f.term(t -> t
                .field("category")
                .value(v -> v.stringValue(categoryFilter))));
          }

          return b;
        })
        .build();

    // 5. Executar busca
    SearchRequest searchReq = new SearchRequest.Builder()
        .index(INDEX_NAME)
        .query(hybridQuery)
        .size(RETRIEVAL_SIZE * 2) // Buscar mais para garantir diversidade
        .build();

    var response = client.search(searchReq, Map.class);

    // 6. Converter hits para SearchResult
    List<SearchResult> results = new ArrayList<>();
    for (var hit : response.hits().hits()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> source = (Map<String, Object>) hit.source();

      // Extrair scores BM25 e k-NN (simplificado - em produção seria mais complexo)
      double totalScore = hit.score() != null ? hit.score() : 0.0;
      double bm25Score = totalScore * 0.4; // Estimativa
      double knnScore = totalScore * 0.6; // Estimativa

      SearchResult result = new SearchResult(hit.id(), source, bm25Score, knnScore);
      results.add(result);
    }

    return results;
  }

  /**
   * Exibe resultados formatados
   */
  private void displayResults(List<SearchResult> results, String queryText) {
    System.out.println("\n" + "═".repeat(80));
    System.out.println("📊 TOP " + results.size() + " RESULTADOS (Ranqueados por LTR)");
    System.out.println("═".repeat(80));

    System.out.println(String.format("\n%-4s | %-8s | %-8s | %-8s | %-10s | %s",
        "Rank", "LTR", "BM25", "k-NN", "Category", "Title"));
    System.out.println("─".repeat(80));

    int rank = 1;
    for (SearchResult result : results) {
      System.out.println(String.format("%-4d | %8.2f | %8.3f | %8.3f | %-10s | %s",
          rank++,
          result.getLtrScore(),
          result.getBm25Score(),
          result.getKnnScore(),
          truncate(result.getCategory(), 10),
          truncate(result.getTitle(), 40)));

      // Mostrar descrição (indentada)
      System.out.println(String.format("     | %8s | %8s | %8s | %-10s | 📝 %s",
          "", "", "", "",
          truncate(result.getDescription(), 50)));

      System.out.println("─".repeat(80));
    }

    // Mostrar feature importance do primeiro resultado (para debug)
    if (!results.isEmpty()) {
      SearchResult topResult = results.get(0);
      System.out.println("\n🔍 ANÁLISE DETALHADA DO TOP 1:");
      System.out.println("═".repeat(80));
      featureExtractor.printFeatureSummary(topResult.getFeatures());
      System.out.println(ltrModel.explainPrediction(topResult.getFeatures(), topResult.getLtrScore()));
    }

    System.out.println("\n💡 INTERPRETAÇÃO DOS SCORES:");
    System.out.println("   • LTR Score (0-100): Score final após ML model (quanto maior, melhor)");
    System.out.println("   • BM25: Relevância léxica (match de palavras)");
    System.out.println("   • k-NN: Relevância semântica (similaridade conceitual)");
    System.out.println("═".repeat(80));
  }

  /**
   * Busca simplificada (sem filtro)
   */
  public List<SearchResult> search(String queryText, int topK) throws Exception {
    return search(queryText, topK, null);
  }

  /**
   * Trunca texto para exibição
   */
  private String truncate(String text, int maxLength) {
    if (text == null)
      return "";
    if (text.length() <= maxLength)
      return text;
    return text.substring(0, maxLength - 3) + "...";
  }

  /**
   * Retorna explicação do modelo LTR
   */
  public String explainModel() {
    return ltrModel.explainModel();
  }
}

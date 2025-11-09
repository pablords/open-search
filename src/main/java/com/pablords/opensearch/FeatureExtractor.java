package com.pablords.opensearch;

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Extrator de Features para Learning to Rank (LTR)
 * Extrai múltiplas features de cada documento candidato
 */
public class FeatureExtractor {

  /**
   * Extrai todas as features de um documento candidato
   * 
   * @param result     Resultado da busca híbrida
   * @param query      Texto da query do usuário
   * @param allResults Todos os resultados (para features relativas)
   * @return Vetor de features
   */
  public FeatureVector extractFeatures(SearchResult result, String query, List<SearchResult> allResults) {
    String title = result.getTitle().toLowerCase();
    String description = result.getDescription().toLowerCase();
    String category = result.getCategory().toLowerCase();
    String queryLower = query.toLowerCase();

    FeatureVector.Builder builder = FeatureVector.builder();

    // ============================================================
    // GRUPO 1: FEATURES DE RELEVÂNCIA (BM25 + Semântica)
    // ============================================================

    // Feature 1-2: Scores originais normalizados
    builder.add("bm25_score", normalizeScore(result.getBm25Score(), allResults, SearchResult::getBm25Score));
    builder.add("knn_score", normalizeScore(result.getKnnScore(), allResults, SearchResult::getKnnScore));

    // Feature 3: Score híbrido (média ponderada)
    builder.add("hybrid_score", result.getBm25Score() * 0.4 + result.getKnnScore() * 0.6);

    // ============================================================
    // GRUPO 2: FEATURES DE MATCH TEXTUAL
    // ============================================================

    // Feature 4: Exact match no título
    builder.add("exact_match_title", title.contains(queryLower));

    // Feature 5: Exact match na descrição
    builder.add("exact_match_description", description.contains(queryLower));

    // Feature 6: Exact match na categoria
    builder.add("exact_match_category", category.contains(queryLower));

    // Feature 7: Term coverage (% de termos da query que aparecem no documento)
    builder.add("term_coverage", calculateTermCoverage(queryLower, title + " " + description));

    // Feature 8: Query length (queries curtas vs longas)
    builder.add("query_length", queryLower.split("\\s+").length);

    // ============================================================
    // GRUPO 3: FEATURES DE QUALIDADE DO TEXTO
    // ============================================================

    // Feature 9: Title length (títulos muito curtos ou longos são suspeitos)
    builder.add("title_length", title.length());

    // Feature 10: Description length
    builder.add("description_length", description.length());

    // Feature 11: Ratio query/title length
    double titleLenRatio = queryLower.length() / Math.max(1.0, title.length());
    builder.add("query_title_ratio", Math.min(2.0, titleLenRatio)); // Cap at 2.0

    // ============================================================
    // GRUPO 4: FEATURES DE POSIÇÃO/CONTEXTO
    // ============================================================

    // Feature 12: First word match (primeira palavra da query aparece no título?)
    String firstWord = queryLower.split("\\s+")[0];
    builder.add("first_word_match", title.contains(firstWord));

    // Feature 13: Has numbers (queries com números geralmente querem match exato)
    builder.add("query_has_numbers", queryLower.matches(".*\\d+.*"));
    builder.add("title_has_numbers", title.matches(".*\\d+.*"));

    // Feature 14: Brand detection (marca conhecida na query?)
    builder.add("has_known_brand", detectKnownBrand(queryLower));

    // ============================================================
    // GRUPO 5: FEATURES DE POPULARIDADE (SIMULADAS)
    // ============================================================
    // Em produção, essas viriam de um banco de dados

    // Feature 15: Popularidade simulada baseada no docId (para demo)
    int docNumber = extractDocNumber(result.getDocId());
    builder.add("simulated_popularity", simulatePopularity(docNumber));

    // Feature 16: Qualidade simulada (produtos mais antigos = mais reviews)
    builder.add("simulated_quality", simulateQuality(docNumber));

    // Feature 17: Click-through rate simulado
    builder.add("simulated_ctr", simulateCTR(docNumber, category));

    return builder.build();
  }

  /**
   * Normaliza um score entre 0 e 1 baseado nos valores do conjunto
   */
  private double normalizeScore(double score, List<SearchResult> allResults,
      ToDoubleFunction<SearchResult> scoreExtractor) {
    if (allResults.isEmpty())
      return 0.0;

    double max = allResults.stream()
        .mapToDouble(scoreExtractor)
        .max()
        .orElse(1.0);

    double min = allResults.stream()
        .mapToDouble(scoreExtractor)
        .min()
        .orElse(0.0);

    if (max - min < 0.001)
      return 0.5; // Evitar divisão por zero

    return (score - min) / (max - min);
  }

  /**
   * Calcula percentual de termos da query que aparecem no documento
   */
  private double calculateTermCoverage(String query, String document) {
    String[] queryTerms = query.split("\\s+");
    if (queryTerms.length == 0)
      return 0.0;

    long matchingTerms = Arrays.stream(queryTerms)
        .filter(term -> document.contains(term))
        .count();

    return (double) matchingTerms / queryTerms.length;
  }

  /**
   * Detecta se a query contém uma marca conhecida
   */
  private boolean detectKnownBrand(String query) {
    String[] knownBrands = {
        "samsung", "apple", "sony", "lg", "dell", "hp", "lenovo", "asus",
        "nike", "adidas", "puma", "reebok", "levi", "calvin", "tommy",
        "microsoft", "google", "amazon", "netflix", "spotify"
    };

    return Arrays.stream(knownBrands)
        .anyMatch(query::contains);
  }

  /**
   * Extrai número do documento (ex: "doc_42" -> 42)
   */
  private int extractDocNumber(String docId) {
    try {
      return Integer.parseInt(docId.replaceAll("\\D+", ""));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Simula popularidade do produto (em produção, viria de analytics)
   * Usa uma função que favorece produtos do meio da lista
   */
  private double simulatePopularity(int docNumber) {
    // Distribuição normal simulada: produtos do meio são mais populares
    double normalized = docNumber / 100.0;
    return Math.exp(-Math.pow(normalized - 0.5, 2) / 0.1) * 10;
  }

  /**
   * Simula qualidade/rating do produto (1.0 a 5.0)
   */
  private double simulateQuality(int docNumber) {
    // Varia entre 3.0 e 5.0 com padrão pseudo-aleatório
    int seed = docNumber * 17 + 42;
    double quality = 3.0 + ((seed % 100) / 50.0);
    return Math.min(5.0, quality);
  }

  /**
   * Simula CTR (Click-Through Rate) baseado em categoria
   */
  private double simulateCTR(int docNumber, String category) {
    // Eletrônicos geralmente têm CTR mais alto
    double baseCTR = category.contains("eletrônico") ? 0.15 : 0.10;

    // Adiciona variação pseudo-aleatória
    int seed = docNumber * 23 + 17;
    double variation = (seed % 10) / 100.0;

    return Math.min(0.30, baseCTR + variation);
  }

  /**
   * Imprime resumo das features para debug
   */
  public void printFeatureSummary(FeatureVector features) {
    System.out.println("\n📊 Features Extraídas:");
    System.out.println("──────────────────────────────────────────────");

    Map<String, Double> allFeatures = features.getAll();

    // Agrupar por tipo
    System.out.println("🎯 Relevância:");
    printFeatureGroup(allFeatures, "bm25_score", "knn_score", "hybrid_score");

    System.out.println("\n📝 Match Textual:");
    printFeatureGroup(allFeatures, "exact_match_title", "exact_match_description",
        "exact_match_category", "term_coverage", "query_length");

    System.out.println("\n📏 Qualidade do Texto:");
    printFeatureGroup(allFeatures, "title_length", "description_length", "query_title_ratio");

    System.out.println("\n🔍 Contexto:");
    printFeatureGroup(allFeatures, "first_word_match", "query_has_numbers",
        "title_has_numbers", "has_known_brand");

    System.out.println("\n⭐ Popularidade (Simulada):");
    printFeatureGroup(allFeatures, "simulated_popularity", "simulated_quality", "simulated_ctr");

    System.out.println("──────────────────────────────────────────────");
  }

  private void printFeatureGroup(Map<String, Double> features, String... featureNames) {
    for (String name : featureNames) {
      Double value = features.get(name);
      if (value != null) {
        System.out.printf("   %-25s : %.3f\n", name, value);
      }
    }
  }
}

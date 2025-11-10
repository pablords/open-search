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
    // GRUPO 5: FEATURES DE POPULARIDADE (REAIS DO DATASET)
    // ============================================================
    
    // Feature 15: Popularidade (clicks reais do produto) - NORMALIZADA
    double popularity = getDoubleFromSource(result.getSource(), "popularity", 1000.0);
    double normalizedPopularity = normalizePopularity(popularity, allResults);
    builder.add("popularity", normalizedPopularity);

    // Feature 16: Qualidade (rating real do produto) - NORMALIZADA para 0-1
    double quality = getDoubleFromSource(result.getSource(), "quality", 4.0);
    double normalizedQuality = normalizeQuality(quality);
    builder.add("quality", normalizedQuality);

    // Feature 17: Click-through rate real - NORMALIZADA
    double ctr = getDoubleFromSource(result.getSource(), "ctr", 0.05);
    double normalizedCtr = normalizeCtr(ctr, allResults);
    builder.add("ctr", normalizedCtr);

    return builder.build();
  }

  /**
   * Normaliza popularidade baseada no conjunto de resultados
   */
  private double normalizePopularity(double popularity, List<SearchResult> allResults) {
    if (allResults.isEmpty()) return 0.5;
    
    // Extrair popularidades de todos os resultados
    double maxPop = allResults.stream()
        .mapToDouble(r -> getDoubleFromSource(r.getSource(), "popularity", 1000.0))
        .max()
        .orElse(10000.0);
    
    double minPop = allResults.stream()
        .mapToDouble(r -> getDoubleFromSource(r.getSource(), "popularity", 1000.0))
        .min()
        .orElse(100.0);
    
    if (maxPop - minPop < 1.0) return 0.5;
    
    return (popularity - minPop) / (maxPop - minPop);
  }

  /**
   * Normaliza qualidade de 0-5 para 0-1
   */
  private double normalizeQuality(double quality) {
    // Assumindo que qualidade varia de 0 a 5
    // Mapear para 0-1 onde 3.0 = mínimo aceitável
    return Math.max(0.0, Math.min(1.0, (quality - 3.0) / 2.0));
  }

  /**
   * Normaliza CTR baseada no conjunto de resultados
   */
  private double normalizeCtr(double ctr, List<SearchResult> allResults) {
    if (allResults.isEmpty()) return 0.5;
    
    // Extrair CTRs de todos os resultados
    double maxCtr = allResults.stream()
        .mapToDouble(r -> getDoubleFromSource(r.getSource(), "ctr", 0.05))
        .max()
        .orElse(0.20);
    
    double minCtr = allResults.stream()
        .mapToDouble(r -> getDoubleFromSource(r.getSource(), "ctr", 0.05))
        .min()
        .orElse(0.01);
    
    if (maxCtr - minCtr < 0.001) return 0.5;
    
    return (ctr - minCtr) / (maxCtr - minCtr);
  }

  /**
   * Extrai um valor numérico do source do documento OpenSearch
   */
  private double getDoubleFromSource(Map<String, Object> source, String field, double defaultValue) {
    if (source == null || !source.containsKey(field)) {
      return defaultValue;
    }
    
    Object value = source.get(field);
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    
    // Tentar converter string para número
    if (value instanceof String) {
      try {
        return Double.parseDouble((String) value);
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    
    return defaultValue;
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

    System.out.println("\n⭐ Popularidade (Dataset Real):");
    printFeatureGroup(allFeatures, "popularity", "quality", "ctr");

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

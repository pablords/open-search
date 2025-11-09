package com.pablords.opensearch;

import java.util.*;

/**
 * Modelo LTR (Learning to Rank) Simplificado
 * 
 * Em produção, você usaria XGBoost, LightGBM ou LambdaMART
 * Este é um modelo baseado em pesos aprendidos/configurados manualmente
 * 
 * ARQUITETURA:
 * - Recebe um FeatureVector (17 features)
 * - Aplica pesos aprendidos
 * - Retorna score final de ranking
 */
public class LTRModel {

  // Pesos das features (em produção, viriam de treinamento ML)
  private final Map<String, Double> featureWeights;

  /**
   * Construtor com pesos padrão otimizados para e-commerce
   */
  public LTRModel() {
    this.featureWeights = getDefaultWeights();
  }

  /**
   * Construtor com pesos customizados
   */
  public LTRModel(Map<String, Double> customWeights) {
    this.featureWeights = customWeights;
  }

  /**
   * Prediz o score LTR para um documento
   * 
   * @param features Vetor de features extraídas
   * @return Score final de ranking (0.0 a 100.0)
   */
  public double predict(FeatureVector features) {
    double score = 0.0;

    // Score = Σ(weight_i * feature_i)
    for (Map.Entry<String, Double> entry : features.getAll().entrySet()) {
      String featureName = entry.getKey();
      double featureValue = entry.getValue();
      double weight = featureWeights.getOrDefault(featureName, 0.0);

      score += weight * featureValue;
    }

    // Aplicar função de ativação (sigmoid para normalizar entre 0-100)
    return sigmoid(score) * 100;
  }

  /**
   * Prediz scores para múltiplos documentos
   */
  public List<Double> predictBatch(List<FeatureVector> featureVectors) {
    List<Double> scores = new ArrayList<>();
    for (FeatureVector features : featureVectors) {
      scores.add(predict(features));
    }
    return scores;
  }

  /**
   * Pesos padrão otimizados para e-commerce
   * 
   * Baseados em boas práticas:
   * - BM25 e k-NN são importantes, mas não dominantes
   * - Exact match no título é crucial
   * - Term coverage é muito importante
   * - Popularidade e qualidade têm peso moderado
   */
  private Map<String, Double> getDefaultWeights() {
    Map<String, Double> weights = new HashMap<>();

    // ============================================================
    // GRUPO 1: RELEVÂNCIA (35% do score total)
    // ============================================================
    weights.put("bm25_score", 4.0); // BM25 é importante para matches exatos
    weights.put("knn_score", 5.0); // k-NN um pouco mais importante (semântica)
    weights.put("hybrid_score", 3.0); // Score combinado

    // ============================================================
    // GRUPO 2: MATCH TEXTUAL (30% do score total)
    // ============================================================
    weights.put("exact_match_title", 8.0); // MUITO IMPORTANTE: palavra exata no título
    weights.put("exact_match_description", 2.0); // Menos importante na descrição
    weights.put("exact_match_category", 1.5); // Categoria match ajuda
    weights.put("term_coverage", 6.0); // Cobertura de termos é crucial
    weights.put("query_length", 0.5); // Peso baixo (feature contextual)

    // ============================================================
    // GRUPO 3: QUALIDADE DO TEXTO (10% do score total)
    // ============================================================
    weights.put("title_length", 0.01); // Títulos médios são melhores
    weights.put("description_length", 0.005); // Descrições longas são melhores
    weights.put("query_title_ratio", 1.0); // Ratio similar é bom sinal

    // ============================================================
    // GRUPO 4: CONTEXTO (15% do score total)
    // ============================================================
    weights.put("first_word_match", 4.0); // Primeira palavra é importante
    weights.put("query_has_numbers", 1.0); // Queries com número precisam match exato
    weights.put("title_has_numbers", 0.5); // Títulos com número são específicos
    weights.put("has_known_brand", 3.0); // Marca conhecida aumenta confiança

    // ============================================================
    // GRUPO 5: POPULARIDADE (10% do score total)
    // ============================================================
    weights.put("simulated_popularity", 2.0); // Produtos populares sobem
    weights.put("simulated_quality", 1.5); // Qualidade importa
    weights.put("simulated_ctr", 2.5); // CTR alto é forte sinal

    return weights;
  }

  /**
   * Função sigmoid para normalização
   * Mapeia (-∞, +∞) para (0, 1)
   */
  private double sigmoid(double x) {
    return 1.0 / (1.0 + Math.exp(-x / 10.0)); // Dividido por 10 para suavizar
  }

  /**
   * Retorna explicação do modelo (feature importance)
   */
  public String explainModel() {
    StringBuilder sb = new StringBuilder();
    sb.append("\n🤖 LTR MODEL - Feature Importance\n");
    sb.append("═══════════════════════════════════════════════════\n");

    // Ordenar features por peso
    List<Map.Entry<String, Double>> sortedWeights = new ArrayList<>(featureWeights.entrySet());
    sortedWeights.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

    sb.append(String.format("%-30s | %s\n", "Feature", "Weight"));
    sb.append("─".repeat(51) + "\n");

    for (Map.Entry<String, Double> entry : sortedWeights) {
      String importance = getImportanceLabel(entry.getValue());
      sb.append(String.format("%-30s | %.2f %s\n",
          entry.getKey(), entry.getValue(), importance));
    }

    sb.append("═══════════════════════════════════════════════════\n");
    sb.append("💡 Total features: " + featureWeights.size() + "\n");
    sb.append("📊 Score range: 0-100 (após sigmoid)\n");

    return sb.toString();
  }

  private String getImportanceLabel(double weight) {
    if (weight >= 6.0)
      return "🔥🔥🔥 (CRÍTICO)";
    if (weight >= 4.0)
      return "🔥🔥 (MUITO ALTO)";
    if (weight >= 2.0)
      return "🔥 (ALTO)";
    if (weight >= 1.0)
      return "⚡ (MÉDIO)";
    return "• (BAIXO)";
  }

  /**
   * Explica a predição de um documento específico
   */
  public String explainPrediction(FeatureVector features, double finalScore) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n🔍 EXPLICAÇÃO DA PREDIÇÃO\n");
    sb.append("═══════════════════════════════════════════════════\n");
    sb.append(String.format("Score Final: %.2f / 100\n", finalScore));
    sb.append("─".repeat(51) + "\n");
    sb.append(String.format("%-25s | %8s | %8s | %10s\n",
        "Feature", "Value", "Weight", "Contrib."));
    sb.append("─".repeat(51) + "\n");

    // Calcular contribuição de cada feature
    List<FeatureContribution> contributions = new ArrayList<>();
    for (Map.Entry<String, Double> entry : features.getAll().entrySet()) {
      String name = entry.getKey();
      double value = entry.getValue();
      double weight = featureWeights.getOrDefault(name, 0.0);
      double contribution = value * weight;
      contributions.add(new FeatureContribution(name, value, weight, contribution));
    }

    // Ordenar por contribuição absoluta
    contributions.sort((a, b) -> Double.compare(
        Math.abs(b.contribution), Math.abs(a.contribution)));

    // Mostrar top 10 contribuições
    for (int i = 0; i < Math.min(10, contributions.size()); i++) {
      FeatureContribution fc = contributions.get(i);
      sb.append(String.format("%-25s | %8.3f | %8.2f | %10.3f %s\n",
          fc.name, fc.value, fc.weight, fc.contribution,
          fc.contribution > 5 ? "🔥" : ""));
    }

    sb.append("═══════════════════════════════════════════════════\n");

    return sb.toString();
  }

  private static class FeatureContribution {
    String name;
    double value;
    double weight;
    double contribution;

    FeatureContribution(String name, double value, double weight, double contribution) {
      this.name = name;
      this.value = value;
      this.weight = weight;
      this.contribution = contribution;
    }
  }

  /**
   * Carrega pesos de um arquivo (para modelos treinados)
   * Em produção, você carregaria de um arquivo XGBoost/LightGBM
   */
  public static LTRModel loadFromFile(String path) {
    // TODO: Implementar carregamento de modelo real
    // Por enquanto, retorna modelo padrão
    System.out.println("⚠️  Modelo de arquivo não implementado. Usando pesos padrão.");
    return new LTRModel();
  }

  /**
   * Salva pesos em arquivo (para persistência)
   */
  public void saveToFile(String path) {
    // TODO: Implementar salvamento de modelo
    System.out.println("⚠️  Salvamento de modelo não implementado.");
  }
}

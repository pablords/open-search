package com.pablords.opensearch;

import java.util.Map;

/**
 * Representa um resultado de busca com informações completas
 * Usado para passar dados entre as etapas de retrieval e reranking
 */
public class SearchResult {
  private final String docId;
  private final Map<String, Object> source;
  private final double bm25Score;
  private final double knnScore;
  private double ltrScore;
  private FeatureVector features;

  public SearchResult(String docId, Map<String, Object> source, double bm25Score, double knnScore) {
    this.docId = docId;
    this.source = source;
    this.bm25Score = bm25Score;
    this.knnScore = knnScore;
    this.ltrScore = 0.0;
  }

  // Getters
  public String getDocId() {
    return docId;
  }

  public Map<String, Object> getSource() {
    return source;
  }

  public double getBm25Score() {
    return bm25Score;
  }

  public double getKnnScore() {
    return knnScore;
  }

  public double getLtrScore() {
    return ltrScore;
  }

  public FeatureVector getFeatures() {
    return features;
  }

  public String getTitle() {
    return (String) source.getOrDefault("title", "");
  }

  public String getDescription() {
    return (String) source.getOrDefault("description", "");
  }

  public String getCategory() {
    return (String) source.getOrDefault("category", "");
  }

  // Setters para LTR
  public void setLtrScore(double ltrScore) {
    this.ltrScore = ltrScore;
  }

  public void setFeatures(FeatureVector features) {
    this.features = features;
  }

  @Override
  public String toString() {
    return String.format("SearchResult{id=%s, title=%s, bm25=%.3f, knn=%.3f, ltr=%.3f}",
        docId, getTitle(), bm25Score, knnScore, ltrScore);
  }
}

package com.pablords.opensearch;

import java.util.HashMap;
import java.util.Map;

/**
 * Representa um vetor de features para LTR (Learning to Rank)
 * Cada documento candidato tem seu vetor de features extraído
 */
public class FeatureVector {
  private final Map<String, Double> features;

  private FeatureVector(Map<String, Double> features) {
    this.features = features;
  }

  public static Builder builder() {
    return new Builder();
  }

  public double get(String featureName) {
    return features.getOrDefault(featureName, 0.0);
  }

  public Map<String, Double> getAll() {
    return new HashMap<>(features);
  }

  public int size() {
    return features.size();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("FeatureVector{");
    features.forEach((k, v) -> sb.append(k).append("=").append(String.format("%.3f", v)).append(", "));
    if (!features.isEmpty()) {
      sb.setLength(sb.length() - 2);
    }
    sb.append("}");
    return sb.toString();
  }

  public static class Builder {
    private final Map<String, Double> features = new HashMap<>();

    public Builder add(String name, double value) {
      features.put(name, value);
      return this;
    }

    public Builder add(String name, boolean value) {
      features.put(name, value ? 1.0 : 0.0);
      return this;
    }

    public Builder add(String name, int value) {
      features.put(name, (double) value);
      return this;
    }

    public FeatureVector build() {
      return new FeatureVector(features);
    }
  }
}

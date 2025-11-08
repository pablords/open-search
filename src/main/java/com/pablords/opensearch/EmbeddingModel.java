package com.pablords.opensearch;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe Helper para carregar um modelo de embedding e gerar vetores.
 * Otimizado para produção com cache LRU e processamento em batch.
 */
public class EmbeddingModel {

    // Configuração do modelo
    // OPÇÃO 1: MiniLM - Rápido, 384 dimensões (atual)
    public static final String MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2";
    public static final int VECTOR_DIMENSION = 384;
    
    // OPÇÃO 2: Multilingual - Melhor para português, 768 dimensões
    // public static final String MODEL_NAME = "sentence-transformers/paraphrase-multilingual-mpnet-base-v2";
    // public static final int VECTOR_DIMENSION = 768;
    
    // OPÇÃO 3: MPNet - Melhor qualidade inglês, 768 dimensões
    // public static final String MODEL_NAME = "sentence-transformers/all-mpnet-base-v2";
    // public static final int VECTOR_DIMENSION = 768;

    private final ZooModel<String, float[]> model;
    private final Predictor<String, float[]> predictor;
    
    // Cache LRU para embeddings frequentes
    private final Map<String, float[]> embeddingCache;
    private final int cacheSize;

    /**
     * Construtor com cache padrão de 1000 embeddings
     */
    public EmbeddingModel() throws MalformedModelException, ModelNotFoundException, IOException {
        this(1000);
    }
    
    /**
     * Construtor com tamanho de cache customizado
     * @param cacheSize Número máximo de embeddings a manter em cache
     */
    public EmbeddingModel(int cacheSize) throws MalformedModelException, ModelNotFoundException, IOException {
        System.out.println("Carregando modelo de embedding: " + MODEL_NAME);
        this.cacheSize = cacheSize;
        
        // Inicializar cache LRU
        this.embeddingCache = new LinkedHashMap<String, float[]>(cacheSize + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > cacheSize;
            }
        };

        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optApplication(Application.NLP.TEXT_EMBEDDING)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/" + MODEL_NAME)
                .optEngine("PyTorch")
                .optProgress(new ProgressBar())
                .build();

        this.model = criteria.loadModel();
        this.predictor = model.newPredictor();

        System.out.println("Modelo carregado com cache de " + cacheSize + " embeddings.");
    }

    /**
     * Transforma um texto em um vetor de embeddings (com cache)
     * @param text Texto para gerar embedding
     * @return Array de floats representando o embedding
     */
    public float[] embed(String text) throws TranslateException {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Texto não pode ser nulo ou vazio");
        }
        
        // Normalizar texto para melhor hit no cache
        String normalizedText = text.trim().toLowerCase();
        
        // Verificar cache primeiro
        float[] cached = embeddingCache.get(normalizedText);
        if (cached != null) {
            return cached;
        }
        
        // Gerar embedding se não estiver em cache
        float[] embedding = predictor.predict(text);
        
        // Armazenar no cache
        embeddingCache.put(normalizedText, embedding);
        
        return embedding;
    }
    
    /**
     * Processa múltiplos textos em batch (mais eficiente)
     * @param texts Lista de textos para processar
     * @return Lista de embeddings correspondentes
     */
    public List<float[]> embedBatch(List<Map<String, String>> texts) throws TranslateException {
        List<float[]> embeddings = new ArrayList<>(texts.size());
        List<String> textsToProcess = new ArrayList<>();
        List<Integer> indicesToProcess = new ArrayList<>();
        
        // Separar textos que já estão em cache dos que precisam ser processados
        for (int i = 0; i < texts.size(); i++) {
            Map<String, String> textMap = texts.get(i);
            String text = textMap.get("title");
            if (text == null || text.trim().isEmpty()) {
                throw new IllegalArgumentException("Texto na posição " + i + " é nulo ou vazio");
            }
            
            String normalizedText = text.trim().toLowerCase();
            float[] cached = embeddingCache.get(normalizedText);
            
            if (cached != null) {
                embeddings.add(cached);
            } else {
                embeddings.add(null); // Placeholder
                textsToProcess.add(text);
                indicesToProcess.add(i);
            }
        }
        
        // Processar textos não cacheados
        // TODO: Implementar batch prediction real quando disponível no DJL
        // Por enquanto, processa um por um
        for (int i = 0; i < textsToProcess.size(); i++) {
            String text = textsToProcess.get(i);
            int originalIndex = indicesToProcess.get(i);
            
            float[] embedding = predictor.predict(text);
            embeddings.set(originalIndex, embedding);
            
            // Adicionar ao cache
            embeddingCache.put(text.trim().toLowerCase(), embedding);
        }
        
        return embeddings;
    }
    
    /**
     * Limpa o cache de embeddings
     */
    public void clearCache() {
        embeddingCache.clear();
        System.out.println("Cache de embeddings limpo.");
    }
    
    /**
     * Retorna estatísticas do cache
     */
    public CacheStats getCacheStats() {
        return new CacheStats(embeddingCache.size(), cacheSize);
    }
    
    /**
     * Classe para estatísticas do cache
     */
    public static class CacheStats {
        public final int currentSize;
        public final int maxSize;
        public final double usagePercentage;
        
        public CacheStats(int currentSize, int maxSize) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.usagePercentage = (double) currentSize / maxSize * 100;
        }
        
        @Override
        public String toString() {
            return String.format("Cache: %d/%d embeddings (%.1f%% usado)", 
                currentSize, maxSize, usagePercentage);
        }
    }

    public void close() {
        this.predictor.close();
        this.model.close();
        this.embeddingCache.clear();
    }
}
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

/**
 * Classe Helper para carregar um modelo de embedding e gerar vetores.
 */
public class EmbeddingModel {

    // O modelo que vamos usar. 
    // "all-MiniLM-L6-v2" é rápido e tem 384 dimensões
    public static final String MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2";
    public static final int VECTOR_DIMENSION = 384; // Importante!

    private final ZooModel<String, float[]> model;
    private final Predictor<String, float[]> predictor;

    public EmbeddingModel() throws MalformedModelException, ModelNotFoundException, IOException {
        System.out.println("Carregando modelo de embedding: " + MODEL_NAME);

        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optApplication(Application.NLP.TEXT_EMBEDDING)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/" + MODEL_NAME)
                .optEngine("PyTorch")
                .optProgress(new ProgressBar())
                .build();

        this.model = criteria.loadModel();
        this.predictor = model.newPredictor();

        System.out.println("Modelo carregado.");
    }

    /**
     * Transforma um texto em um vetor de embeddings
     */
    public float[] embed(String text) throws TranslateException {
        return predictor.predict(text);
    }

    public void close() {
        this.predictor.close();
        this.model.close();
    }
}
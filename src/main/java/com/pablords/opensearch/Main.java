package com.pablords.opensearch;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import static com.pablords.opensearch.SemanticSearchOpenSearch.*;

public class Main {
  public static void main(String[] args) throws Exception {

    // --- 1. Inicializar o Modelo de Embedding ---
    EmbeddingModel embeddingModel = new EmbeddingModel();

    // --- 2. Inicializar o Cliente OpenSearch ---
    final HttpHost host = new HttpHost("http", "localhost", 9200);
    final ApacheHttpClient5TransportBuilder transportBuilder = ApacheHttpClient5TransportBuilder.builder(host);

    // Desativamos a segurança no Docker, então não precisamos de credenciais

    final OpenSearchClient client = new OpenSearchClient(transportBuilder.build());

    System.out.println("Conectado ao OpenSearch!");

    try {
      // --- 3. Deletar índice existente e criar novo ---
      deleteIndexIfExists(client);
      createKnnIndex(client, EmbeddingModel.VECTOR_DIMENSION);

      // --- 4. Indexar Documentos ---
      indexDocuments(client, embeddingModel);

      // --- 5. Fazer a Busca Semântica! ---
      String queryText = "um animal de estimação feliz";
      searchByVector(client, embeddingModel, queryText);

      String queryText2 = "comida italiana deliciosa";
      searchByVector(client, embeddingModel, queryText2);

    } catch (Exception e) {
      System.err.println("Erro ao executar: " + e.getMessage());
      e.printStackTrace();
    } finally {
      embeddingModel.close();
    }
  }
}
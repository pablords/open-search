package com.pablords.opensearch;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import static com.pablords.opensearch.SemanticSearchOpenSearch.*;

/**
 * Exemplo de uso do OpenSearch com busca semântica
 * Otimizado para produção com cache de embeddings e bulk indexing
 */
public class Main {
  public static void main(String[] args) throws Exception {

    System.out.println("=== OpenSearch Semantic Search Demo (Production Ready) ===\n");

    // --- 1. Inicializar o Modelo de Embedding com cache ---
    // Cache de 1000 embeddings para reutilização
    EmbeddingModel embeddingModel = new EmbeddingModel(1000);

    // --- 2. Inicializar o Cliente OpenSearch ---
    final HttpHost host = new HttpHost("http", "localhost", 9200);
    final ApacheHttpClient5TransportBuilder transportBuilder = ApacheHttpClient5TransportBuilder.builder(host);

    final OpenSearchClient client = new OpenSearchClient(transportBuilder.build());

    System.out.println("✓ Conectado ao OpenSearch!");

    try {
      // --- 3. Preparar índice ---
      deleteIndexIfExists(client);
      createKnnIndex(client, EmbeddingModel.VECTOR_DIMENSION);

      // --- 4. Indexar Documentos (usando Bulk API) ---
      indexDocuments(client, embeddingModel);

      // --- 5. Demonstrar buscas semânticas ---
      System.out.println("\n" + "=".repeat(60));
      System.out.println("DEMONSTRAÇÃO DE BUSCAS SEMÂNTICAS");
      System.out.println("=".repeat(60));

      // Busca 1: Animal de estimação
      searchByVector(client, embeddingModel, "um animal de estimação feliz");

      // Busca 2: Comida italiana (usando cache - será mais rápido)
      searchByVector(client, embeddingModel, "comida italiana deliciosa");

      // Busca 3: Mesma query anterior (hit no cache - ainda mais rápido)
      searchByVector(client, embeddingModel, "comida italiana deliciosa");
      
      // Busca 4: Treinamento de cachorro (retornar mais resultados)
      searchByVector(client, embeddingModel, "treinar cachorro filhote", 4);

      // --- 6. Estatísticas finais ---
      System.out.println("\n" + "=".repeat(60));
      System.out.println("ESTATÍSTICAS FINAIS");
      System.out.println("=".repeat(60));
      System.out.println("📊 " + embeddingModel.getCacheStats());
      System.out.println("✓ Demo concluída com sucesso!");

    } catch (Exception e) {
      System.err.println("\n❌ Erro durante execução: " + e.getMessage());
      e.printStackTrace();
    } finally {
      embeddingModel.close();
      System.out.println("\n🔒 Recursos liberados.");
    }
  }
}
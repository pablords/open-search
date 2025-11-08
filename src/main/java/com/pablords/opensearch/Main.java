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

      // --- 5. Demonstrar buscas híbridas (BM25 + Semântica) ---
      System.out.println("\n" + "=".repeat(60));
      System.out.println("DEMONSTRAÇÃO DE BUSCA HÍBRIDA (BM25 + k-NN)");
      System.out.println("=".repeat(60));

      // Busca 1: Termo específico (vai acionar BM25)
      hybridSearch(client, embeddingModel, "fone bluetooth cancelamento ruído", 5);

      // // Busca 2: Conceito semântico (vai acionar k-NN)
      // hybridSearch(client, embeddingModel, "dispositivo para ouvir música sem fio", 5);

      // // Busca 3: Com filtro de categoria
      // hybridSearch(client, embeddingModel, "presente para corredor", 5, "Esportes");

      // // Busca 4: Busca em categoria de livros
      // hybridSearch(client, embeddingModel, "história ciência ficção", 5, "Livros");

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
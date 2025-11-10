package com.pablords.opensearch;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import static com.pablords.opensearch.Bootstrap.*;

/**
 * Demo completa: Busca Híbrida + LTR (Learning to Rank)
 * 
 * ARQUITETURA EM 3 ETAPAS:
 * 1. RETRIEVAL: BM25 + k-NN (recupera ~200 candidatos)
 * 2. FEATURE EXTRACTION: Extrai 17+ features por documento
 * 3. RE-RANKING: Aplica modelo LTR e reordena resultados
 */
public class Main {
  public static void main(String[] args) throws Exception {

    System.out.println("═".repeat(80));
    System.out.println("🚀 OpenSearch: Busca Híbrida + LTR (Estado da Arte)");
    System.out.println("═".repeat(80));
    System.out.println();

    // --- 1. Inicializar o Modelo de Embedding com cache ---
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

      // --- 5. Inicializar Sistema de Busca Híbrida + LTR ---
      HybridSearchWithLTR searchEngine = new HybridSearchWithLTR(client, embeddingModel);
      
      // Mostrar explicação do modelo LTR
      System.out.println(searchEngine.explainModel());

      // --- 6. DEMONSTRAÇÃO: Busca Híbrida + LTR ---
      
      // Busca 1: Query com termos específicos + conceito
      searchEngine.search("notebook", 5);
      
      // Busca 2: Query conceitual (vai explorar a semântica)
      // searchEngine.search("dispositivo para ouvir música sem fio", 5);
      
      // // Busca 3: Query com filtro de categoria
      // searchEngine.search("presente para corredor", 5, "Esportes");
      
      // // Busca 4: Query em categoria específica
      // searchEngine.search("livro sobre futuro", 5, "Livros");

      // --- 7. Estatísticas finais ---
      System.out.println("\n" + "═".repeat(80));
      System.out.println("📊 ESTATÍSTICAS FINAIS");
      System.out.println("═".repeat(80));
      System.out.println("Cache de Embeddings: " + embeddingModel.getCacheStats());
      System.out.println("✓ Demo concluída com sucesso!");
      System.out.println("═".repeat(80));

    } catch (Exception e) {
      System.err.println("\n❌ Erro durante execução: " + e.getMessage());
      e.printStackTrace();
    } finally {
      embeddingModel.close();
      System.out.println("\n🔒 Recursos liberados.");
    }
  }
}
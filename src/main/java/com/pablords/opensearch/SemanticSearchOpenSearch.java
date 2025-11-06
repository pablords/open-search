package com.pablords.opensearch;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.KnnQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemanticSearchOpenSearch {

  private static final String INDEX_NAME = "semantic-search-demo";
  private static final String VECTOR_FIELD = "text_vector";

  public static void deleteIndexIfExists(OpenSearchClient client) throws Exception {
    if (client.indices().exists(new ExistsRequest.Builder().index(INDEX_NAME).build()).value()) {
      System.out.println("Deletando índice existente: " + INDEX_NAME);
      client.indices().delete(d -> d.index(INDEX_NAME));
      System.out.println("Índice deletado.");
    }
  }

  public static void createKnnIndex(OpenSearchClient client, int vectorDim) throws Exception {

    // Verificar se o índice já existe
    if (client.indices().exists(new ExistsRequest.Builder().index(INDEX_NAME).build()).value()) {
      System.out.println("Índice '" + INDEX_NAME + "' já existe. Pulando criação.");
      return;
    }

    System.out.println("Criando índice: " + INDEX_NAME);

    // Criar o índice com k-NN habilitado
    // IMPORTANTE: Precisa habilitar index.knn nas settings
    CreateIndexRequest createReq = new CreateIndexRequest.Builder()
        .index(INDEX_NAME)
        .settings(s -> s
            .index(i -> i
                .knn(true) // Habilitar k-NN no índice
            )
        )
        .mappings(m -> m
            .properties(VECTOR_FIELD, p -> p
                .knnVector(kv -> kv
                    .dimension(vectorDim)
                    .method(method -> method
                        .name("hnsw") // Algoritmo HNSW
                        .spaceType("cosinesimil") // Similaridade de cosseno
                        .engine("lucene")
                    )
                )
            )
            .properties("text_original", p -> p
                .text(t -> t)
            )
        )
        .build();

    client.indices().create(createReq);
    System.out.println("Índice criado com sucesso.");
  }

  public static void indexDocuments(OpenSearchClient client, EmbeddingModel model) throws Exception {
    System.out.println("\n--- Indexando Documentos ---");

    List<String> docs = Arrays.asList(
        "Meu cachorro é meu melhor amigo. Ele ama correr.", // Doc 1
        "O gato está dormindo no raio de sol.", // Doc 2
        "Eu amo cozinhar pizza de pepperoni em casa.", // Doc 3
        "Como treinar seu novo filhote de cão." // Doc 4
    );

    for (int i = 0; i < docs.size(); i++) {
      String text = docs.get(i);

      // 1. Gerar o vetor
      float[] vector = model.embed(text);
      System.out.println("Documento " + i + " - Vetor gerado com " + vector.length + " dimensões");

      // 2. Criar o corpo do documento para o OpenSearch
      Map<String, Object> docBody = new HashMap<>();
      docBody.put("text_original", text);
      docBody.put(VECTOR_FIELD, vector); // Passa o vetor

      // 3. Indexar
      IndexRequest<Map<String, Object>> req = new IndexRequest.Builder<Map<String, Object>>()
          .index(INDEX_NAME)
          .id("doc_" + i)
          .document(docBody)
          .build();

      var response = client.index(req);
      System.out.println("Documento " + i + " indexado: " + response.result());
    }

    System.out.println("Documentos indexados. Refreshing...");
    // Forçar o refresh para a busca funcionar imediatamente (não faça em produção!)
    client.indices().refresh(r -> r.index(INDEX_NAME));
  }

  public static void searchByVector(OpenSearchClient client, EmbeddingModel model, String queryText) throws Exception {
    System.out.println("\n--- Busca Semântica por: '" + queryText + "' ---");

    // 1. Gerar o vetor da *query*
    float[] queryVector = model.embed(queryText);
    System.out.println("Vetor de busca gerado com " + queryVector.length + " dimensões");

    // 2. Construir a Query k-NN
    // Esta query diz: "Encontre os k (3) vetores mais próximos do meu queryVector"
    KnnQuery knnQuery = new KnnQuery.Builder()
        .field(VECTOR_FIELD)
        .vector(queryVector)
        .k(3) // Pedir os 3 vizinhos mais próximos
        .build();

    Query query = new Query.Builder().knn(knnQuery).build();

    // 3. Executar a Busca
    SearchRequest searchReq = new SearchRequest.Builder()
        .index(INDEX_NAME)
        .query(query)
        .size(3) // Limitar resultados
        .build();

    // 4. Exibir Resultados
    var response = client.search(searchReq, Map.class);

    System.out.println("Total de resultados: " + response.hits().total().value());

    if (response.hits().hits().isEmpty()) {
      System.out.println("Nenhum resultado encontrado.");
    } else {
      for (Hit<?> hit : response.hits().hits()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) hit.source();
        System.out.printf(
            "Score: %.4f \t Doc: %s\n",
            hit.score(),
            source.get("text_original"));
      }
    }
  }
}
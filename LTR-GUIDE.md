# Learning to Rank (LTR) no OpenSearch

## 📚 O que é Learning to Rank?

**Learning to Rank (LTR)** é uma técnica de Machine Learning que aprende a melhor ordem de classificação dos resultados de busca baseada em features extraídas e feedback do usuário.

### Por que usar LTR?

A busca híbrida atual combina BM25 + k-NN com pesos fixos. LTR permite:
- ✅ **Personalização**: Aprender o peso ideal para cada feature
- ✅ **Adaptação**: Melhorar com feedback real dos usuários
- ✅ **Contexto**: Considerar múltiplas features simultaneamente
- ✅ **Performance**: Otimizar para métricas de negócio (CTR, conversão, etc)

---

## 🏗️ Arquitetura LTR

```
┌─────────────────────────────────────────────────────────────┐
│                    PROCESSO LTR                              │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  1. FEATURE EXTRACTION                                        │
│     ┌──────────────────────────────────────────┐            │
│     │ Query + Documento                         │            │
│     │  ↓                                        │            │
│     │ • BM25 score (title)                     │            │
│     │ • BM25 score (description)               │            │
│     │ • k-NN cosine similarity                 │            │
│     │ • TF-IDF normalizados                    │            │
│     │ • Query-doc length ratio                 │            │
│     │ • Category match                          │            │
│     │ • Freshness (se aplicável)               │            │
│     └──────────────────────────────────────────┘            │
│                         ↓                                     │
│  2. MODELO DE RANKING                                        │
│     ┌──────────────────────────────────────────┐            │
│     │ Algoritmos:                               │            │
│     │ • LambdaMART (recomendado)               │            │
│     │ • RankSVM                                │            │
│     │ • XGBoost / LightGBM                     │            │
│     └──────────────────────────────────────────┘            │
│                         ↓                                     │
│  3. RERANKING                                                │
│     ┌──────────────────────────────────────────┐            │
│     │ Score final = f(features)                │            │
│     │ Ordena resultados                        │            │
│     └──────────────────────────────────────────┘            │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 Features Recomendadas para E-commerce

### 1. **Features de Relevância Textual**
```java
- bm25_title_score: Score BM25 no campo title
- bm25_description_score: Score BM25 no campo description
- exact_match_title: Boolean se query aparece exatamente no título
- term_coverage: % de termos da query que aparecem no documento
```

### 2. **Features de Relevância Semântica**
```java
- cosine_similarity: Similaridade coseno entre embeddings
- vector_distance: Distância euclidiana normalizada
```

### 3. **Features de Popularidade/Qualidade**
```java
- click_count: Número de cliques no produto
- conversion_rate: Taxa de conversão do produto
- avg_rating: Avaliação média
- review_count: Número de avaliações
- sales_rank: Ranking de vendas
```

### 4. **Features de Contexto**
```java
- category_match: Boolean se categoria corresponde ao contexto
- price_range: Faixa de preço normalizada
- stock_availability: Disponibilidade em estoque
- freshness: Quão recente é o produto
```

### 5. **Features de Query**
```java
- query_length: Número de palavras na query
- query_category_confidence: Confiança do classificador de categoria
- is_branded_query: Boolean se contém marca conhecida
```

---

## 🔧 Implementação no OpenSearch

### Opção 1: Plugin LTR do OpenSearch (Recomendado)

O OpenSearch tem um plugin oficial de LTR:

```bash
# Instalar plugin LTR
bin/opensearch-plugin install \
  https://github.com/opensearch-project/opensearch-learning-to-rank-base/releases/download/v2.6.0/opensearch-learning-to-rank-base-2.6.0.zip
```

### Opção 2: Reranking em Java (Mais Controle)

Implementar reranking customizado na aplicação:

```java
public class LTRReranker {
    private XGBoostModel model;
    
    public List<SearchResult> rerank(
        List<SearchResult> candidates,
        String query,
        Map<String, Object> context
    ) {
        // 1. Extrair features para cada candidato
        List<FeatureVector> features = candidates.stream()
            .map(doc -> extractFeatures(doc, query, context))
            .collect(Collectors.toList());
        
        // 2. Aplicar modelo
        List<Double> scores = model.predict(features);
        
        // 3. Reordenar por score
        return reorderByScores(candidates, scores);
    }
    
    private FeatureVector extractFeatures(
        SearchResult doc, 
        String query,
        Map<String, Object> context
    ) {
        return FeatureVector.builder()
            .add("bm25_title", doc.getBM25Score("title"))
            .add("bm25_description", doc.getBM25Score("description"))
            .add("cosine_sim", doc.getCosineSimilarity())
            .add("exact_match", hasExactMatch(query, doc.getTitle()))
            .add("click_count", doc.getMetadata("clicks"))
            .add("category_match", categoryMatches(context, doc))
            .build();
    }
}
```

---

## 📊 Pipeline de Treinamento LTR

### 1. Coletar Dados de Treinamento

```java
// Formato: query, documento, relevância (0-4)
{
  "query": "fone bluetooth",
  "doc_id": "doc_123",
  "features": {
    "bm25_title": 8.5,
    "bm25_desc": 3.2,
    "cosine_sim": 0.87,
    "clicks": 150,
    "conversions": 12
  },
  "label": 3  // 0=irrelevante, 4=perfeito
}
```

#### Fontes de Labels:
1. **Cliques** (click-through rate)
2. **Tempo na página** (dwell time)
3. **Conversões** (purchases)
4. **Avaliações manuais** (human judgments)
5. **A/B testing** results

### 2. Extrair Features

```python
# Script Python para extrair features
import pandas as pd
from opensearchpy import OpenSearch

def extract_features(query, doc_id):
    # Executar query no OpenSearch
    response = client.search(
        index="products",
        body={
            "query": {
                "multi_match": {
                    "query": query,
                    "fields": ["title", "description"]
                }
            },
            "_source": ["title", "description", "category"],
            "explain": True  # Retorna scores detalhados
        }
    )
    
    # Extrair features dos explain scores
    features = {
        "bm25_title": extract_score(response, "title"),
        "bm25_desc": extract_score(response, "description"),
        "query_length": len(query.split()),
        # ... outras features
    }
    
    return features
```

### 3. Treinar Modelo

```python
import xgboost as xgb
from sklearn.model_selection import train_test_split

# Carregar dados
df = pd.read_csv("training_data.csv")

X = df[feature_columns]
y = df['relevance_label']

# Split
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2)

# Treinar XGBoost
model = xgb.XGBRanker(
    objective='rank:pairwise',
    learning_rate=0.1,
    n_estimators=100,
    max_depth=6
)

model.fit(X_train, y_train, group=train_groups)

# Salvar modelo
model.save_model("ltr_model.json")
```

### 4. Avaliar Modelo

```python
from sklearn.metrics import ndcg_score

# Predições
y_pred = model.predict(X_test)

# Métricas
ndcg = ndcg_score([y_test], [y_pred])
print(f"NDCG@10: {ndcg}")

# Feature importance
importance = model.feature_importances_
for feature, score in zip(feature_columns, importance):
    print(f"{feature}: {score:.4f}")
```

---

## 🚀 Integração com o Código Atual

### Passo 1: Adicionar Feature Store

```java
public class FeatureStore {
    private Map<String, Map<String, Double>> productFeatures;
    
    public void loadFeatures(String path) {
        // Carregar features pré-calculadas
        // clicks, conversions, ratings, etc
    }
    
    public Map<String, Double> getFeatures(String docId) {
        return productFeatures.getOrDefault(docId, new HashMap<>());
    }
}
```

### Passo 2: Modificar hybridSearch para coletar features

```java
public static void hybridSearchWithLTR(
    OpenSearchClient client,
    EmbeddingModel model,
    LTRReranker reranker,
    String queryText,
    int k
) throws Exception {
    // 1. Busca híbrida (recuperar mais candidatos)
    var candidates = executeHybridSearch(client, model, queryText, k * 3);
    
    // 2. Extrair features de cada candidato
    List<FeatureVector> features = candidates.stream()
        .map(doc -> extractAllFeatures(doc, queryText))
        .collect(Collectors.toList());
    
    // 3. Aplicar LTR reranking
    List<Double> ltrScores = reranker.predict(features);
    
    // 4. Reordenar por LTR score
    List<SearchResult> reranked = reorderByScores(candidates, ltrScores);
    
    return reranked.subList(0, Math.min(k, reranked.size()));
}
```

### Passo 3: Adicionar logging para treinamento

```java
public class SearchLogger {
    public void logSearchEvent(
        String query,
        String docId,
        Map<String, Double> features,
        SearchEvent event // CLICK, PURCHASE, etc
    ) {
        // Log para arquivo/database para treinamento futuro
        String json = String.format(
            "{\"query\":\"%s\",\"doc\":\"%s\",\"features\":%s,\"event\":\"%s\",\"ts\":%d}",
            query, docId, toJson(features), event, System.currentTimeMillis()
        );
        logger.info(json);
    }
}
```

---

## 📈 Métricas de Avaliação

### NDCG (Normalized Discounted Cumulative Gain)
- Melhor métrica para ranking
- Considera posição e relevância
- NDCG@10 típico: 0.7-0.9

### MAP (Mean Average Precision)
- Precisão média em diferentes pontos
- Bom para avaliar recall

### MRR (Mean Reciprocal Rank)
- Foca no primeiro resultado relevante
- Útil para queries navegacionais

---

## 🎓 Próximos Passos

1. **Imediato** (já implementado):
   - ✅ Busca híbrida BM25 + k-NN
   - ✅ Multi-field boosting (title^3, description^1)
   - ✅ Filtros por categoria

2. **Curto Prazo** (1-2 semanas):
   - [ ] Implementar feature extraction completa
   - [ ] Adicionar logging de eventos (cliques, conversões)
   - [ ] Coletar dados de treinamento inicial

3. **Médio Prazo** (1 mês):
   - [ ] Treinar primeiro modelo LTR
   - [ ] Implementar reranking em Java
   - [ ] A/B test: híbrido vs híbrido+LTR

4. **Longo Prazo** (3+ meses):
   - [ ] Retreinamento automático
   - [ ] Personalização por usuário
   - [ ] Features de contexto avançadas

---

## 📚 Referências

1. [OpenSearch LTR Plugin](https://opensearch.org/docs/latest/search-plugins/ltr/)
2. [XGBoost Ranking](https://xgboost.readthedocs.io/en/stable/tutorials/learning_to_rank.html)
3. [Learning to Rank Paper (Liu, 2009)](https://www.microsoft.com/en-us/research/publication/learning-to-rank-for-information-retrieval/)
4. [Practical LTR Guide](https://blog.vespa.ai/learning-to-rank-guide/)

---

## 💡 Exemplo Completo de Features

```java
public class ProductFeatureExtractor {
    public FeatureVector extract(SearchHit hit, String query) {
        Map<String, Object> source = hit.getSourceAsMap();
        
        return FeatureVector.builder()
            // Text relevance
            .add("bm25_title", extractScore(hit, "title"))
            .add("bm25_description", extractScore(hit, "description"))
            .add("exact_match_title", exactMatch(query, source.get("title")))
            .add("term_coverage", termCoverage(query, source))
            
            // Semantic relevance
            .add("cosine_similarity", hit.getScore())
            
            // Popularity
            .add("view_count", getMetric(source, "views"))
            .add("click_count", getMetric(source, "clicks"))
            .add("conversion_rate", getMetric(source, "conversions") / getMetric(source, "views"))
            
            // Quality
            .add("avg_rating", getMetric(source, "rating"))
            .add("review_count", getMetric(source, "reviews"))
            
            // Context
            .add("in_stock", source.get("stock") > 0 ? 1.0 : 0.0)
            .add("category_match", categoryMatch(query, source.get("category")))
            .add("price_range", normalizePriceRange(source.get("price")))
            
            // Query features
            .add("query_length", query.split(" ").length)
            .add("has_brand", hasBrand(query))
            
            .build();
    }
}
```

Este é um guia completo para implementar LTR no seu sistema de busca! 🚀
